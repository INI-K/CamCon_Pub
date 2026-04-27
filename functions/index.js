/**
 * CamCon Cloud Functions — 결제 영수증 검증 및 Firestore 기록.
 *
 * 보안 모델:
 * - 클라이언트는 Firestore subscriptions 컬렉션에 직접 쓸 수 없음 (firestore.rules: allow write: if false).
 * - 본 callable 함수가 Google Play Developer API로 영수증을 검증하고 Admin SDK로 Firestore에 기록한다.
 * - purchaseToken은 절대 평문 저장하지 않는다 — SHA-256 해시로 멱등성 키만 보관.
 * - ADMIN/REFERRER 티어는 본 함수에서 부여될 수 없다 (BASIC/PRO만 매핑).
 */

const crypto = require('crypto');
const { onCall, HttpsError } = require('firebase-functions/v2/https');
const { logger } = require('firebase-functions/v2');
const admin = require('firebase-admin');
const { google } = require('googleapis');

admin.initializeApp();

const PACKAGE_NAME = 'com.inik.camcon';
const REGION = 'asia-northeast3';

/** productId → SubscriptionTier 매핑. BASIC/PRO만 허용. */
const PRODUCT_TIER_MAP = Object.freeze({
    camcon_basic_monthly: 'BASIC',
    camcon_basic_yearly: 'BASIC',
    camcon_pro_monthly: 'PRO',
    camcon_pro_yearly: 'PRO',
});

/** 권한 부여 가능한 구독 상태. RTDN 없이도 Active/유예기간은 활성으로 처리. */
const ACCEPTABLE_SUBSCRIPTION_STATES = new Set([
    'SUBSCRIPTION_STATE_ACTIVE',
    'SUBSCRIPTION_STATE_IN_GRACE_PERIOD',
]);

let cachedAndroidPublisher = null;

/** ADC(Application Default Credentials)로 Google Play Developer API 클라이언트 생성. */
async function getAndroidPublisher() {
    if (cachedAndroidPublisher) return cachedAndroidPublisher;
    const auth = new google.auth.GoogleAuth({
        scopes: ['https://www.googleapis.com/auth/androidpublisher'],
    });
    const authClient = await auth.getClient();
    cachedAndroidPublisher = google.androidpublisher({ version: 'v3', auth: authClient });
    return cachedAndroidPublisher;
}

function sha256Hex(value) {
    return crypto.createHash('sha256').update(value).digest('hex');
}

/**
 * verifyAndRecordPurchase
 * - 입력: { purchaseToken: string, productId: string }
 * - 인증: Firebase Auth (request.auth.uid 필수)
 * - 처리: Google Play Developer API로 구독 검증 → 검증 통과 시 Firestore에 기록
 * - 멱등성: purchase_tokens/{sha256(token)} 문서로 클레임 잠금 (다른 uid가 동일 토큰을 신고하면 거부)
 */
exports.verifyAndRecordPurchase = onCall(
    { region: REGION, cors: false },
    async (request) => {
        const uid = request.auth?.uid;
        if (!uid) {
            throw new HttpsError('unauthenticated', '로그인이 필요합니다.');
        }

        const { purchaseToken, productId } = request.data ?? {};
        if (typeof purchaseToken !== 'string' || purchaseToken.length === 0) {
            throw new HttpsError('invalid-argument', 'purchaseToken은 필수입니다.');
        }
        if (typeof productId !== 'string' || !PRODUCT_TIER_MAP[productId]) {
            throw new HttpsError('invalid-argument', `허용되지 않은 productId: ${productId}`);
        }

        const tier = PRODUCT_TIER_MAP[productId];
        const tokenHash = sha256Hex(purchaseToken);

        // 1) 멱등성 — 같은 토큰을 다른 사용자가 이미 클레임한 경우 즉시 거부
        const claimRef = admin.firestore().collection('purchase_tokens').doc(tokenHash);
        const claimDoc = await claimRef.get();
        if (claimDoc.exists && claimDoc.data().uid !== uid) {
            logger.error('purchaseToken 다른 사용자가 이미 클레임함', {
                uid,
                ownerUid: claimDoc.data().uid,
                tokenHash,
            });
            throw new HttpsError('permission-denied', '이 구매는 다른 사용자에게 귀속되어 있습니다.');
        }

        // 2) Google Play Developer API로 구독 검증
        let subscription;
        try {
            const publisher = await getAndroidPublisher();
            const resp = await publisher.purchases.subscriptionsv2.get({
                packageName: PACKAGE_NAME,
                token: purchaseToken,
            });
            subscription = resp.data;
        } catch (err) {
            logger.error('Google Play subscriptions.get 실패', { uid, error: err?.message });
            throw new HttpsError('failed-precondition', '구매 검증에 실패했습니다.');
        }

        if (!ACCEPTABLE_SUBSCRIPTION_STATES.has(subscription.subscriptionState)) {
            logger.warn('비활성 구독 상태 — 권한 미부여', {
                uid,
                state: subscription.subscriptionState,
            });
            throw new HttpsError(
                'permission-denied',
                `구독이 활성 상태가 아닙니다: ${subscription.subscriptionState}`
            );
        }

        // 3) productId 정합성 — 검증된 구독에 본인이 신고한 productId가 실제 포함되어야 함
        const lineItems = subscription.lineItems ?? [];
        const matchedLineItem = lineItems.find((li) => li.productId === productId);
        if (!matchedLineItem) {
            logger.error('productId 불일치 — 권한 미부여', {
                uid,
                claimed: productId,
                actual: lineItems.map((li) => li.productId),
            });
            throw new HttpsError('permission-denied', 'productId가 구매 영수증과 일치하지 않습니다.');
        }

        const startTime = subscription.startTime ? new Date(subscription.startTime) : null;
        const expiryTime = matchedLineItem.expiryTime ? new Date(matchedLineItem.expiryTime) : null;
        const autoRenew = matchedLineItem.autoRenewingPlan?.autoRenewEnabled === true;

        // expiryTime이 있고 이미 과거이면 isActive=false (RTDN 부재 시 만료 즉시 반영을 위해)
        // ACCEPTABLE_SUBSCRIPTION_STATES 검사로 이 시점에 도달했더라도 안전 차원에서 한 번 더 확인.
        const isActive = expiryTime ? expiryTime.getTime() > Date.now() : true;

        // 4) Firestore 기록 (Admin SDK는 rules 우회)
        const userSubRef = admin.firestore()
            .collection('users').doc(uid)
            .collection('subscriptions').doc('current');

        const subscriptionData = {
            tier,
            productId,
            startDate: startTime
                ? admin.firestore.Timestamp.fromDate(startTime)
                : admin.firestore.FieldValue.serverTimestamp(),
            endDate: expiryTime ? admin.firestore.Timestamp.fromDate(expiryTime) : null,
            autoRenew,
            isActive,
            updatedAt: admin.firestore.FieldValue.serverTimestamp(),
            updatedBy: 'verifyAndRecordPurchase',
        };

        const claimData = {
            uid,
            productId,
            claimedAt: admin.firestore.FieldValue.serverTimestamp(),
        };

        const batch = admin.firestore().batch();
        batch.set(userSubRef, subscriptionData, { merge: true });
        batch.set(claimRef, claimData, { merge: true });
        await batch.commit();

        logger.info('구독 기록 완료', { uid, tier, productId });

        return {
            ok: true,
            tier,
            isActive,
            endDate: expiryTime ? expiryTime.toISOString() : null,
        };
    }
);
