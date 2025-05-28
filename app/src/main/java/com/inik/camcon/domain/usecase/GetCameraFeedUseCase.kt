import com.inik.camcon.domain.model.Camera
import com.inik.camcon.domain.repository.CameraRepository
import kotlinx.coroutines.flow.Flow

class GetCameraFeedUseCase(private val repository: CameraRepository) {
    operator fun invoke(): Flow<List<Camera>> = repository.getCameraFeed()
}
