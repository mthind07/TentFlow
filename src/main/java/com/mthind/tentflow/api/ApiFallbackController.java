package com.mthind.tentflow.api;

import com.mthind.tentflow.exception.ResourceNotFoundException;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ApiFallbackController {

    @RequestMapping({"/api", "/api/{*path}"})
    public void routeNotFound() {
        throw new ResourceNotFoundException(
                "No API endpoint exists at this path."
        );
    }
}