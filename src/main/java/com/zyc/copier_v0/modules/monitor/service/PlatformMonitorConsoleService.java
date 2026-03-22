package com.zyc.copier_v0.modules.monitor.service;

import com.zyc.copier_v0.modules.account.config.domain.Mt5AccountRole;
import com.zyc.copier_v0.modules.account.config.entity.Mt5AccountEntity;
import com.zyc.copier_v0.modules.account.config.repository.Mt5AccountRepository;
import com.zyc.copier_v0.modules.copy.engine.api.ExecutionCommandResponse;
import com.zyc.copier_v0.modules.copy.engine.api.ExecutionTraceResponse;
import com.zyc.copier_v0.modules.copy.engine.api.FollowerDispatchOutboxResponse;
import com.zyc.copier_v0.modules.copy.engine.service.CopyEngineService;
import com.zyc.copier_v0.modules.copy.followerexec.api.FollowerExecSessionResponse;
import com.zyc.copier_v0.modules.copy.followerexec.service.FollowerExecWebSocketService;
import com.zyc.copier_v0.modules.monitor.api.MonitorAccountDetailResponse;
import com.zyc.copier_v0.modules.monitor.api.MonitorDashboardResponse;
import com.zyc.copier_v0.modules.monitor.api.Mt5AccountMonitorOverviewResponse;
import com.zyc.copier_v0.modules.monitor.api.Mt5RuntimeStateResponse;
import com.zyc.copier_v0.modules.monitor.api.Mt5WsSessionResponse;
import com.zyc.copier_v0.modules.monitor.domain.Mt5ConnectionStatus;
import com.zyc.copier_v0.modules.user.auth.domain.PlatformUserRole;
import com.zyc.copier_v0.modules.user.auth.entity.PlatformUserEntity;
import com.zyc.copier_v0.modules.user.auth.service.PlatformAuthService;
import java.util.List;
import javax.persistence.EntityNotFoundException;
import javax.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlatformMonitorConsoleService {

    private final PlatformAuthService platformAuthService;
    private final Mt5AccountRepository mt5AccountRepository;
    private final AccountMonitorService accountMonitorService;
    private final CopyEngineService copyEngineService;
    private final FollowerExecWebSocketService followerExecWebSocketService;

    public PlatformMonitorConsoleService(
            PlatformAuthService platformAuthService,
            Mt5AccountRepository mt5AccountRepository,
            AccountMonitorService accountMonitorService,
            CopyEngineService copyEngineService,
            FollowerExecWebSocketService followerExecWebSocketService
    ) {
        this.platformAuthService = platformAuthService;
        this.mt5AccountRepository = mt5AccountRepository;
        this.accountMonitorService = accountMonitorService;
        this.copyEngineService = copyEngineService;
        this.followerExecWebSocketService = followerExecWebSocketService;
    }

    @Transactional(readOnly = true)
    public MonitorDashboardResponse getDashboard(HttpServletRequest request) {
        PlatformUserEntity currentUser = platformAuthService.requireCurrentUser(request);
        Long filterUserId = currentUser.getRole() == PlatformUserRole.ADMIN ? null : currentUser.getId();
        List<Mt5AccountMonitorOverviewResponse> overviews = accountMonitorService.listAccountOverviews(filterUserId, null);

        MonitorDashboardResponse response = new MonitorDashboardResponse();
        response.setTotalAccounts(overviews.size());
        response.setOnlineMasterCount(overviews.stream()
                .filter(this::isMasterLike)
                .filter(item -> item.getConnectionStatus() == Mt5ConnectionStatus.CONNECTED)
                .count());
        response.setOnlineFollowerCount(overviews.stream()
                .filter(this::isFollowerLike)
                .filter(item -> item.getConnectionStatus() == Mt5ConnectionStatus.CONNECTED)
                .count());
        response.setStaleRuntimeStateCount(overviews.stream()
                .filter(item -> item.getConnectionStatus() == Mt5ConnectionStatus.STALE)
                .count());
        response.setPendingDispatchCount(overviews.stream().mapToLong(Mt5AccountMonitorOverviewResponse::getPendingDispatchCount).sum());
        response.setFailedDispatchCount(overviews.stream().mapToLong(Mt5AccountMonitorOverviewResponse::getFailedDispatchCount).sum());
        return response;
    }

    @Transactional(readOnly = true)
    public MonitorAccountDetailResponse getAccountDetail(Long accountId, HttpServletRequest request) {
        Mt5AccountEntity account = loadAccessibleAccount(accountId, request);

        MonitorAccountDetailResponse response = new MonitorAccountDetailResponse();
        response.setOverview(accountMonitorService.listAccountOverviews(account.getUserId(), null).stream()
                .filter(item -> accountId.equals(item.getAccountId()))
                .findFirst()
                .orElse(null));
        response.setRuntimeState(accountMonitorService.listRuntimeStates().stream()
                .filter(item -> accountId.equals(item.getAccountId()))
                .findFirst()
                .orElse(null));
        response.setWsSessions(accountMonitorService.listWsSessions(account.getUserId(), null).stream()
                .filter(item -> accountId.equals(item.getAccountId()))
                .toList());
        response.setFollowerExecSessions(followerExecWebSocketService.listSessions().stream()
                .filter(item -> accountId.equals(item.getFollowerAccountId()))
                .toList());
        return response;
    }

    @Transactional(readOnly = true)
    public List<ExecutionCommandResponse> listCommandsByAccountId(Long accountId, HttpServletRequest request) {
        loadAccessibleAccount(accountId, request);
        return copyEngineService.findByAccountId(accountId);
    }

    @Transactional(readOnly = true)
    public List<FollowerDispatchOutboxResponse> listDispatchesByAccountId(Long accountId, HttpServletRequest request) {
        loadAccessibleAccount(accountId, request);
        return copyEngineService.findDispatchesByAccountId(accountId);
    }

    @Transactional(readOnly = true)
    public ExecutionTraceResponse getOrderTrace(Long masterAccountId, Long masterOrderId, HttpServletRequest request) {
        loadAccessibleAccount(masterAccountId, request);
        return copyEngineService.findOrderTrace(masterAccountId, masterOrderId);
    }

    @Transactional(readOnly = true)
    public ExecutionTraceResponse getPositionTrace(Long masterAccountId, Long masterPositionId, HttpServletRequest request) {
        loadAccessibleAccount(masterAccountId, request);
        return copyEngineService.findPositionTrace(masterAccountId, masterPositionId);
    }

    private Mt5AccountEntity loadAccessibleAccount(Long accountId, HttpServletRequest request) {
        PlatformUserEntity currentUser = platformAuthService.requireCurrentUser(request);
        Mt5AccountEntity account = mt5AccountRepository.findById(accountId)
                .orElseThrow(() -> new EntityNotFoundException("MT5 account not found: " + accountId));
        if (currentUser.getRole() != PlatformUserRole.ADMIN && !currentUser.getId().equals(account.getUserId())) {
            throw new IllegalArgumentException("You do not have access to this MT5 account");
        }
        return account;
    }

    private boolean isMasterLike(Mt5AccountMonitorOverviewResponse response) {
        return response.getAccountRole() == Mt5AccountRole.MASTER || response.getAccountRole() == Mt5AccountRole.BOTH;
    }

    private boolean isFollowerLike(Mt5AccountMonitorOverviewResponse response) {
        return response.getAccountRole() == Mt5AccountRole.FOLLOWER || response.getAccountRole() == Mt5AccountRole.BOTH;
    }
}
