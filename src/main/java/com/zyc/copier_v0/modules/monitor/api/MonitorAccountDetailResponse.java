package com.zyc.copier_v0.modules.monitor.api;

import com.zyc.copier_v0.modules.copy.followerexec.api.FollowerExecSessionResponse;
import java.util.List;
import lombok.Data;

@Data
public class MonitorAccountDetailResponse {

    private Mt5AccountMonitorOverviewResponse overview;
    private Mt5RuntimeStateResponse runtimeState;
    private List<Mt5WsSessionResponse> wsSessions;
    private List<FollowerExecSessionResponse> followerExecSessions;
}
