package kr.co.oruda.onsure.web.core;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Read-only authenticated Web/BFF projection. No mutation or state promotion endpoint lives here. */
@RestController
@RequestMapping("/api/web/v1")
public class CoreReadApiController {
    private final CoreReadProjectionService service;

    public CoreReadApiController(CoreReadProjectionService service) {
        this.service = service;
    }

    @GetMapping("/projects")
    Object projects() {
        return service.projects();
    }

    @GetMapping("/projects/{projectId}")
    Object project(@PathVariable String projectId) {
        return service.project(projectId);
    }

    @GetMapping("/projects/{projectId}/targets")
    Object targets(@PathVariable String projectId) {
        return service.targets(projectId);
    }

    @GetMapping("/projects/{projectId}/targets/{targetId}")
    Object target(@PathVariable String projectId, @PathVariable String targetId) {
        return service.target(projectId, targetId);
    }

    @GetMapping("/projects/{projectId}/targets/{targetId}/assurance")
    Object assurance(@PathVariable String projectId, @PathVariable String targetId) {
        return service.assurance(projectId, targetId);
    }

    @GetMapping("/projects/{projectId}/targets/{targetId}/evidence")
    Object evidence(@PathVariable String projectId, @PathVariable String targetId) {
        return service.evidence(projectId, targetId);
    }
}
