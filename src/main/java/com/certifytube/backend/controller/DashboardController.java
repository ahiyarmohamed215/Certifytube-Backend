package com.certifytube.backend.controller;

import com.certifytube.backend.dto.DashboardResponse;
import com.certifytube.backend.model.UserAccount;
import com.certifytube.backend.service.AuthenticatedUserService;
import com.certifytube.backend.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;
    private final AuthenticatedUserService authenticatedUserService;

    /**
     * GET /api/dashboard
     * GET /api/dashboard?status=ACTIVE
     * GET /api/dashboard?status=COMPLETED,QUIZ_PENDING,CERTIFIED
     *
     * If status param is provided, only sessions with matching statuses are
     * returned.
     * If omitted, all statuses are returned.
     */
    @GetMapping
    public DashboardResponse getDashboard(
            @RequestParam(value = "status", required = false) String statusParam) {
        UserAccount user = authenticatedUserService.currentUser();

        Set<String> statuses = null;
        if (statusParam != null && !statusParam.isBlank()) {
            statuses = Arrays.stream(statusParam.split(","))
                    .map(String::trim)
                    .map(String::toUpperCase)
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toSet());
        }

        return dashboardService.getDashboard(user.getId(), statuses);
    }
}
