package com.zyc.copier_v0.modules.monitor.api;

import lombok.Data;

@Data
public class MonitorDashboardResponse {

    private long totalAccounts;
    private long onlineMasterCount;
    private long onlineFollowerCount;
    private long staleRuntimeStateCount;
    private long pendingDispatchCount;
    private long failedDispatchCount;
}
