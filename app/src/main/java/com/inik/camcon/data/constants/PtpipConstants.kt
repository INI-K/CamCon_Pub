package com.inik.camcon.data.constants

/**
 * PTPIP 프로토콜 상수 정의
 */
object PtpipConstants {
    // 네트워크 설정
    const val DISCOVERY_TIMEOUT = 10000L // 10초
    const val CONNECTION_TIMEOUT = 10000 // 10초
    const val SERVICE_TYPE = "_ptp._tcp"

    // PTPIP 패킷 타입
    const val PTPIP_INIT_COMMAND_REQUEST = 1
    const val PTPIP_INIT_COMMAND_ACK = 2
    const val PTPIP_INIT_EVENT_REQUEST = 3
    const val PTPIP_INIT_EVENT_ACK = 4
    const val PTPIP_OPERATION_REQUEST = 6
    const val PTPIP_OPERATION_RESPONSE = 7

    // PTP 오퍼레이션 코드
    const val PTP_OC_GetDeviceInfo = 0x1001
    const val PTP_OC_OpenSession = 0x1002
    const val PTP_OC_CloseSession = 0x1003
    const val PTP_OC_GetStorageIDs = 0x1004
    const val PTP_OC_GetStorageInfo = 0x1005
    const val PTP_OC_GetNumObjects = 0x1006
    const val PTP_OC_GetObjectHandles = 0x1007
    const val PTP_OC_GetObjectInfo = 0x1008
    const val PTP_OC_GetObject = 0x1009
    const val PTP_OC_DeleteObject = 0x100A
    const val PTP_OC_SendObjectInfo = 0x100C
    const val PTP_OC_SendObject = 0x100D
    const val PTP_OC_InitiateCapture = 0x100E
    const val PTP_OC_FormatStore = 0x100F
    const val PTP_OC_ResetDevice = 0x1010
    const val PTP_OC_SelfTest = 0x1011
    const val PTP_OC_SetObjectProtection = 0x1012
    const val PTP_OC_PowerDown = 0x1013
    const val PTP_OC_GetDevicePropDesc = 0x1014
    const val PTP_OC_GetDevicePropValue = 0x1015
    const val PTP_OC_SetDevicePropValue = 0x1016
    const val PTP_OC_ResetDevicePropValue = 0x1017
    const val PTP_OC_TerminateOpenCapture = 0x1018
    const val PTP_OC_MoveObject = 0x1019
    const val PTP_OC_CopyObject = 0x101A
    const val PTP_OC_GetPartialObject = 0x101B
    const val PTP_OC_InitiateOpenCapture = 0x101C

    // Nikon 전용 PTP 오퍼레이션 코드
    const val PTP_OC_NIKON_GetProfileAllData = 0x9006
    const val PTP_OC_NIKON_SendProfileData = 0x9007
    const val PTP_OC_NIKON_DeleteProfile = 0x9008
    const val PTP_OC_NIKON_SetProfileData = 0x9009
    const val PTP_OC_NIKON_AdvancedTransfer = 0x9010
    const val PTP_OC_NIKON_GetFileInfoInBlock = 0x9011
    const val PTP_OC_NIKON_Capture = 0x90C0
    const val PTP_OC_NIKON_AfDrive = 0x90C1
    const val PTP_OC_NIKON_SetControlMode = 0x90C2
    const val PTP_OC_NIKON_DelImageSDRAM = 0x90C3
    const val PTP_OC_NIKON_GetLargeThumb = 0x90C4
    const val PTP_OC_NIKON_CurveDownload = 0x90C5
    const val PTP_OC_NIKON_CurveUpload = 0x90C6
    const val PTP_OC_NIKON_CheckEvent = 0x90C7
    const val PTP_OC_NIKON_DeviceReady = 0x90C8
    const val PTP_OC_NIKON_SetPreWBData = 0x90C9
    const val PTP_OC_NIKON_GetVendorPropCodes = 0x90CA
    const val PTP_OC_NIKON_AfCaptureSDRAM = 0x90CB
    const val PTP_OC_NIKON_GetPictCtrlData = 0x90CC
    const val PTP_OC_NIKON_SetPictCtrlData = 0x90CD
    const val PTP_OC_NIKON_DelCstPicCtrl = 0x90CE
    const val PTP_OC_NIKON_GetPicCtrlCapability = 0x90CF
    const val PTP_OC_NIKON_GetPreviewImg = 0x9200
    const val PTP_OC_NIKON_StartLiveView = 0x9201
    const val PTP_OC_NIKON_EndLiveView = 0x9202
    const val PTP_OC_NIKON_GetLiveViewImg = 0x9203
    const val PTP_OC_NIKON_MfDrive = 0x9204
    const val PTP_OC_NIKON_ChangeAfArea = 0x9205
    const val PTP_OC_NIKON_AfDriveCancel = 0x9206
    const val PTP_OC_NIKON_InitiateCaptureRecInMedia = 0x9207
    const val PTP_OC_NIKON_GetVendorStorageIDs = 0x9209
    const val PTP_OC_NIKON_StartMovieRecInCard = 0x920a
    const val PTP_OC_NIKON_EndMovieRec = 0x920b
    const val PTP_OC_NIKON_TerminateCapture = 0x920c
    const val PTP_OC_NIKON_GetDevicePTPIPInfo = 0x90E0
    const val PTP_OC_NIKON_GetPartialObjectHiSpeed = 0x9400
    const val PTP_OC_NIKON_GetDevicePropEx = 0x9504
}