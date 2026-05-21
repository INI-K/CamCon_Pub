package com.inik.camcon.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class CameraEndpointTest {

    @Test
    fun `toPtpipCamera preserves ip port and name`() {
        val endpoint = CameraEndpoint(
            ipAddress = "192.168.49.137",
            port = 15740,
            name = "Z6 Camera",
            source = EndpointSource.MDNS,
            manufacturer = "Nikon",
        )

        val ptpip = endpoint.toPtpipCamera()

        assertEquals("192.168.49.137", ptpip.ipAddress)
        assertEquals(15740, ptpip.port)
        assertEquals("Z6 Camera", ptpip.name)
    }

    @Test
    fun `toEndpoint round trip keeps ip and name`() {
        val camera = PtpipCamera(
            ipAddress = "10.0.0.5",
            port = 15740,
            name = "DSC-XYZ"
        )

        val endpoint = camera.toEndpoint()

        assertEquals(camera.ipAddress, endpoint.ipAddress)
        assertEquals(camera.port, endpoint.port)
        assertEquals(camera.name, endpoint.name)
        assertEquals(EndpointSource.SAVED, endpoint.source)
    }

    @Test
    fun `toEndpoint accepts explicit source`() {
        val camera = PtpipCamera("1.2.3.4", 15740, "x")
        val endpoint = camera.toEndpoint(EndpointSource.MANUAL_INPUT)
        assertEquals(EndpointSource.MANUAL_INPUT, endpoint.source)
    }
}
