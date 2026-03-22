package com.zyc.copier_v0.modules.monitor.web;

import com.zyc.copier_v0.modules.copy.engine.api.ExecutionCommandResponse;
import com.zyc.copier_v0.modules.copy.engine.api.ExecutionTraceResponse;
import com.zyc.copier_v0.modules.copy.engine.api.FollowerDispatchOutboxResponse;
import com.zyc.copier_v0.modules.monitor.api.MonitorAccountDetailResponse;
import com.zyc.copier_v0.modules.monitor.api.MonitorDashboardResponse;
import com.zyc.copier_v0.modules.monitor.service.PlatformMonitorConsoleService;
import java.util.List;
import javax.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/monitor")
public class PlatformMonitorConsoleController {

    private final PlatformMonitorConsoleService platformMonitorConsoleService;

    public PlatformMonitorConsoleController(PlatformMonitorConsoleService platformMonitorConsoleService) {
        this.platformMonitorConsoleService = platformMonitorConsoleService;
    }

    @GetMapping("/dashboard")
    public MonitorDashboardResponse getDashboard(HttpServletRequest request) {
        return platformMonitorConsoleService.getDashboard(request);
    }

    @GetMapping("/accounts/{accountId}/detail")
    public MonitorAccountDetailResponse getAccountDetail(@PathVariable Long accountId, HttpServletRequest request) {
        return platformMonitorConsoleService.getAccountDetail(accountId, request);
    }

    @GetMapping("/accounts/{accountId}/commands")
    public List<ExecutionCommandResponse> listCommands(@PathVariable Long accountId, HttpServletRequest request) {
        return platformMonitorConsoleService.listCommandsByAccountId(accountId, request);
    }

    @GetMapping("/accounts/{accountId}/dispatches")
    public List<FollowerDispatchOutboxResponse> listDispatches(@PathVariable Long accountId, HttpServletRequest request) {
        return platformMonitorConsoleService.listDispatchesByAccountId(accountId, request);
    }

    @GetMapping("/traces/order")
    public ExecutionTraceResponse getOrderTrace(
            @RequestParam Long masterAccountId,
            @RequestParam Long masterOrderId,
            HttpServletRequest request
    ) {
        return platformMonitorConsoleService.getOrderTrace(masterAccountId, masterOrderId, request);
    }

    @GetMapping("/traces/position")
    public ExecutionTraceResponse getPositionTrace(
            @RequestParam Long masterAccountId,
            @RequestParam Long masterPositionId,
            HttpServletRequest request
    ) {
        return platformMonitorConsoleService.getPositionTrace(masterAccountId, masterPositionId, request);
    }
}
