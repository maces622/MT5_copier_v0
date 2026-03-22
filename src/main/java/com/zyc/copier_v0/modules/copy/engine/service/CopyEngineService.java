package com.zyc.copier_v0.modules.copy.engine.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.zyc.copier_v0.modules.account.config.cache.CopyRouteSnapshotReader;
import com.zyc.copier_v0.modules.account.config.cache.FollowerRiskCacheSnapshot;
import com.zyc.copier_v0.modules.account.config.cache.FollowerRouteCacheItem;
import com.zyc.copier_v0.modules.account.config.cache.MasterRouteCacheSnapshot;
import com.zyc.copier_v0.modules.account.config.cache.Mt5AccountBindingCacheSnapshot;
import com.zyc.copier_v0.modules.account.config.domain.CopyMode;
import com.zyc.copier_v0.modules.copy.engine.api.ExecutionCommandResponse;
import com.zyc.copier_v0.modules.copy.engine.api.ExecutionTraceResponse;
import com.zyc.copier_v0.modules.copy.engine.api.FollowerDispatchOutboxResponse;
import com.zyc.copier_v0.modules.copy.engine.domain.ExecutionCommandType;
import com.zyc.copier_v0.modules.copy.engine.domain.ExecutionCommandStatus;
import com.zyc.copier_v0.modules.copy.engine.domain.ExecutionRejectReason;
import com.zyc.copier_v0.modules.copy.engine.domain.FollowerDispatchStatus;
import com.zyc.copier_v0.modules.copy.engine.entity.ExecutionCommandEntity;
import com.zyc.copier_v0.modules.copy.engine.entity.FollowerDispatchOutboxEntity;
import com.zyc.copier_v0.modules.copy.engine.event.FollowerDispatchCreatedEvent;
import com.zyc.copier_v0.modules.copy.engine.repository.ExecutionCommandRepository;
import com.zyc.copier_v0.modules.copy.engine.repository.FollowerDispatchOutboxRepository;
import com.zyc.copier_v0.modules.copy.engine.slippage.DispatchSlippagePolicy;
import com.zyc.copier_v0.modules.copy.engine.slippage.DispatchSlippagePolicyResolver;
import com.zyc.copier_v0.modules.monitor.service.Mt5AccountRuntimeStateSnapshot;
import com.zyc.copier_v0.modules.monitor.service.Mt5AccountRuntimeStateStore;
import com.zyc.copier_v0.modules.signal.ingest.domain.Mt5SignalType;
import com.zyc.copier_v0.modules.signal.ingest.domain.NormalizedMt5Signal;
import com.zyc.copier_v0.modules.signal.ingest.event.Mt5SignalAcceptedEvent;
import java.time.Instant;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.Map;
import java.util.stream.Collectors;
import javax.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class CopyEngineService {

    private static final Logger log = LoggerFactory.getLogger(CopyEngineService.class);

    private final CopyRouteSnapshotReader copyRouteSnapshotReader;
    private final ExecutionCommandRepository executionCommandRepository;
    private final FollowerDispatchOutboxRepository followerDispatchOutboxRepository;
    private final DispatchSlippagePolicyResolver dispatchSlippagePolicyResolver;
    private final Mt5AccountRuntimeStateStore runtimeStateStore;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher applicationEventPublisher;

    public CopyEngineService(
            CopyRouteSnapshotReader copyRouteSnapshotReader,
            ExecutionCommandRepository executionCommandRepository,
            FollowerDispatchOutboxRepository followerDispatchOutboxRepository,
            DispatchSlippagePolicyResolver dispatchSlippagePolicyResolver,
            Mt5AccountRuntimeStateStore runtimeStateStore,
            ObjectMapper objectMapper,
            ApplicationEventPublisher applicationEventPublisher
    ) {
        this.copyRouteSnapshotReader = copyRouteSnapshotReader;
        this.executionCommandRepository = executionCommandRepository;
        this.followerDispatchOutboxRepository = followerDispatchOutboxRepository;
        this.dispatchSlippagePolicyResolver = dispatchSlippagePolicyResolver;
        this.runtimeStateStore = runtimeStateStore;
        this.objectMapper = objectMapper;
        this.applicationEventPublisher = applicationEventPublisher;
    }

    @EventListener
    @Transactional
    public void onMt5SignalAccepted(Mt5SignalAcceptedEvent event) {
        NormalizedMt5Signal signal = event.getSignal();
        if (signal.getType() != Mt5SignalType.DEAL && signal.getType() != Mt5SignalType.ORDER) {
            return;
        }

        Optional<Mt5AccountBindingCacheSnapshot> masterAccountOptional = copyRouteSnapshotReader.loadAccountBinding(
                signal.getServer(),
                signal.getLogin()
        );

        if (!masterAccountOptional.isPresent()) {
            log.warn("Skip copy-engine processing because master account is not bound, eventId={}, account={}",
                    signal.getEventId(), signal.getMasterAccountKey());
            return;
        }

        Mt5AccountBindingCacheSnapshot masterAccount = masterAccountOptional.get();
        MasterRouteCacheSnapshot routeSnapshot = copyRouteSnapshotReader.loadMasterRoute(masterAccount.getAccountId());
        if (routeSnapshot.getFollowers().isEmpty()) {
            log.info("No active followers for master account, masterAccountId={}, eventId={}",
                    masterAccount.getAccountId(), signal.getEventId());
            return;
        }

        for (FollowerRouteCacheItem follower : routeSnapshot.getFollowers()) {
            if (executionCommandRepository.existsByMasterEventIdAndFollowerAccountId(
                    signal.getEventId(),
                    follower.getFollowerAccountId()
            )) {
                continue;
            }
            ExecutionCommandEntity command = executionCommandRepository.save(
                    buildCommand(masterAccount.getAccountId(), follower, signal)
            );
            createDispatchOutboxIfNeeded(command, signal, follower);
        }
    }

    @Transactional(readOnly = true)
    public List<ExecutionCommandResponse> findByMasterEventId(String masterEventId) {
        if (!StringUtils.hasText(masterEventId)) {
            return Collections.emptyList();
        }
        return executionCommandRepository.findByMasterEventIdOrderByIdAsc(masterEventId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ExecutionCommandResponse> findByFollowerAccountId(Long followerAccountId) {
        return executionCommandRepository.findByFollowerAccountIdOrderByIdDesc(followerAccountId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ExecutionCommandResponse> findByAccountId(Long accountId) {
        Map<Long, ExecutionCommandEntity> commands = executionCommandRepository.findByMasterAccountIdOrderByIdDesc(accountId).stream()
                .collect(Collectors.toMap(ExecutionCommandEntity::getId, command -> command, (left, right) -> left));
        executionCommandRepository.findByFollowerAccountIdOrderByIdDesc(accountId)
                .forEach(command -> commands.putIfAbsent(command.getId(), command));
        return commands.values().stream()
                .sorted((left, right) -> Long.compare(right.getId(), left.getId()))
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public ExecutionTraceResponse findOrderTrace(Long masterAccountId, Long masterOrderId) {
        if (masterAccountId == null || masterOrderId == null) {
            return emptyTrace(masterAccountId, masterOrderId, null);
        }
        List<ExecutionCommandEntity> commands = executionCommandRepository
                .findByMasterAccountIdAndMasterOrderIdOrderByIdAsc(masterAccountId, masterOrderId);
        return buildTrace(masterAccountId, masterOrderId, null, commands);
    }

    @Transactional(readOnly = true)
    public ExecutionTraceResponse findPositionTrace(Long masterAccountId, Long masterPositionId) {
        if (masterAccountId == null || masterPositionId == null) {
            return emptyTrace(masterAccountId, null, masterPositionId);
        }
        List<ExecutionCommandEntity> commands = executionCommandRepository
                .findByMasterAccountIdAndMasterPositionIdOrderByIdAsc(masterAccountId, masterPositionId);
        return buildTrace(masterAccountId, null, masterPositionId, commands);
    }

    @Transactional(readOnly = true)
    public List<FollowerDispatchOutboxResponse> findDispatchesByFollower(
            Long followerAccountId,
            FollowerDispatchStatus status
    ) {
        List<FollowerDispatchOutboxEntity> dispatches = status == null
                ? followerDispatchOutboxRepository.findByFollowerAccountIdOrderByIdDesc(followerAccountId)
                : followerDispatchOutboxRepository.findByFollowerAccountIdAndStatusOrderByIdAsc(followerAccountId, status);
        return dispatches.stream()
                .map(this::toDispatchResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<FollowerDispatchOutboxResponse> findDispatchesByMasterEventId(String masterEventId) {
        if (!StringUtils.hasText(masterEventId)) {
            return Collections.emptyList();
        }
        return followerDispatchOutboxRepository.findByMasterEventIdOrderByIdAsc(masterEventId).stream()
                .map(this::toDispatchResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<FollowerDispatchOutboxResponse> findDispatchesByAccountId(Long accountId) {
        Map<Long, FollowerDispatchOutboxEntity> dispatches = followerDispatchOutboxRepository.findByFollowerAccountIdOrderByIdDesc(accountId).stream()
                .collect(Collectors.toMap(FollowerDispatchOutboxEntity::getId, dispatch -> dispatch, (left, right) -> left));

        List<Long> relatedCommandIds = executionCommandRepository.findByMasterAccountIdOrderByIdDesc(accountId).stream()
                .map(ExecutionCommandEntity::getId)
                .toList();
        if (!relatedCommandIds.isEmpty()) {
            followerDispatchOutboxRepository.findByExecutionCommandIdInOrderByIdAsc(relatedCommandIds)
                    .forEach(dispatch -> dispatches.putIfAbsent(dispatch.getId(), dispatch));
        }

        return dispatches.values().stream()
                .sorted((left, right) -> Long.compare(right.getId(), left.getId()))
                .map(this::toDispatchResponse)
                .toList();
    }

    @Transactional
    public FollowerDispatchOutboxResponse updateDispatchStatus(
            Long dispatchId,
            FollowerDispatchStatus status,
            String statusMessage
    ) {
        FollowerDispatchOutboxEntity dispatch = followerDispatchOutboxRepository.findById(dispatchId)
                .orElseThrow(() -> new EntityNotFoundException("Follower dispatch not found: " + dispatchId));

        dispatch.setStatus(status);
        dispatch.setStatusMessage(trimToNull(statusMessage));
        Instant now = Instant.now();
        if (status == FollowerDispatchStatus.ACKED) {
            dispatch.setAckedAt(now);
            dispatch.setFailedAt(null);
        } else if (status == FollowerDispatchStatus.FAILED) {
            dispatch.setFailedAt(now);
            dispatch.setAckedAt(null);
        } else {
            dispatch.setAckedAt(null);
            dispatch.setFailedAt(null);
        }

        return toDispatchResponse(followerDispatchOutboxRepository.save(dispatch));
    }

    private ExecutionCommandEntity buildCommand(
            Long masterAccountId,
            FollowerRouteCacheItem follower,
            NormalizedMt5Signal signal
    ) {
        if (signal.getType() == Mt5SignalType.ORDER) {
            return buildOrderCommand(masterAccountId, follower, signal);
        }
        return buildDealCommand(masterAccountId, follower, signal);
    }

    private ExecutionCommandEntity buildDealCommand(
            Long masterAccountId,
            FollowerRouteCacheItem follower,
            NormalizedMt5Signal signal
    ) {
        JsonNode payload = signal.getPayload();
        String masterSymbol = payload.path("symbol").asText();
        String followerSymbol = resolveFollowerSymbol(follower.getRisk(), masterSymbol);
        String action = payload.path("action").asText();
        BigDecimal masterVolume = payload.hasNonNull("volume")
                ? payload.path("volume").decimalValue()
                : null;

        ExecutionCommandEntity command = newBaseCommand(masterAccountId, follower, signal, masterSymbol, followerSymbol);
        command.setSignalType(Mt5SignalType.DEAL);
        command.setCommandType(resolveDealCommandType(action));
        command.setMasterAction(action);
        command.setFollowerAction(resolveFollowerAction(action, follower.getRisk().isReverseFollow()));
        command.setMasterDealId(readLong(payload, "deal"));
        command.setMasterOrderId(readLong(payload, "order"));
        command.setMasterPositionId(readLong(payload, "position"));
        command.setRequestedPrice(readDecimal(payload, "price"));

        Rejection rejection = validateAndComputeDealVolume(
                follower.getCopyMode(),
                follower.getRisk(),
                followerSymbol,
                masterVolume,
                payload,
                follower.getFollowerAccountId()
        );
        if (rejection.isRejected()) {
            command.setStatus(ExecutionCommandStatus.REJECTED);
            command.setRejectReason(rejection.reason());
            command.setRejectMessage(rejection.message());
            command.setRequestedVolume(rejection.volume());
        } else {
            command.setStatus(ExecutionCommandStatus.READY);
            command.setRequestedVolume(rejection.volume());
        }

        return command;
    }

    private ExecutionCommandEntity buildOrderCommand(
            Long masterAccountId,
            FollowerRouteCacheItem follower,
            NormalizedMt5Signal signal
    ) {
        JsonNode payload = signal.getPayload();
        String masterSymbol = payload.path("symbol").asText();
        String followerSymbol = resolveFollowerSymbol(follower.getRisk(), masterSymbol);
        String eventName = payload.path("event").asText();
        String scope = payload.path("scope").asText();
        Integer orderType = readInteger(payload, "order_type");
        boolean pendingOrder = isPendingOrder(orderType);
        boolean marketOrder = isMarketOrder(orderType);

        if (marketOrder && ("ORDER_ADD".equalsIgnoreCase(eventName) || "ORDER_DELETE".equalsIgnoreCase(eventName))) {
            return buildIgnoredOrderCommand(
                    masterAccountId, follower, signal, masterSymbol, followerSymbol, eventName, scope
            );
        }

        ExecutionCommandEntity command = newBaseCommand(masterAccountId, follower, signal, masterSymbol, followerSymbol);
        command.setSignalType(Mt5SignalType.ORDER);
        command.setMasterAction(eventName);
        command.setMasterOrderId(readLong(payload, "order"));
        command.setRequestedVolume(resolveRequestedOrderVolume(payload, pendingOrder));
        command.setRequestedPrice(readDecimal(payload, "price_open"));
        command.setRequestedSl(readDecimal(payload, "sl"));
        command.setRequestedTp(readDecimal(payload, "tp"));
        command.setMasterPositionId(readLong(payload, "position"));

        OrderInstruction instruction = resolveOrderInstruction(eventName, pendingOrder, marketOrder);
        command.setCommandType(instruction.commandType());
        command.setFollowerAction(instruction.followerAction());

        Rejection rejection = validateOrderSignal(
                follower.getCopyMode(),
                follower.getRisk(),
                followerSymbol,
                eventName,
                scope,
                pendingOrder,
                marketOrder,
                command.getRequestedVolume(),
                payload,
                follower.getFollowerAccountId()
        );
        if (rejection.isRejected()) {
            command.setStatus(ExecutionCommandStatus.REJECTED);
            command.setRejectReason(rejection.reason());
            command.setRejectMessage(rejection.message());
            if (rejection.volume() != null) {
                command.setRequestedVolume(rejection.volume());
            }
        } else {
            command.setStatus(ExecutionCommandStatus.READY);
            if (rejection.volume() != null) {
                command.setRequestedVolume(rejection.volume());
            }
        }
        return command;
    }

    private ExecutionCommandEntity buildIgnoredOrderCommand(
            Long masterAccountId,
            FollowerRouteCacheItem follower,
            NormalizedMt5Signal signal,
            String masterSymbol,
            String followerSymbol,
            String eventName,
            String scope
    ) {
        ExecutionCommandEntity command = newBaseCommand(masterAccountId, follower, signal, masterSymbol, followerSymbol);
        command.setSignalType(Mt5SignalType.ORDER);
        command.setCommandType(ExecutionCommandType.UPDATE_PENDING_ORDER);
        command.setMasterAction(eventName);
        command.setFollowerAction("IGNORED");
        command.setMasterOrderId(readLong(signal.getPayload(), "order"));
        command.setRequestedPrice(readDecimal(signal.getPayload(), "price_open"));
        command.setRequestedSl(readDecimal(signal.getPayload(), "sl"));
        command.setRequestedTp(readDecimal(signal.getPayload(), "tp"));
        command.setStatus(ExecutionCommandStatus.REJECTED);
        command.setRejectReason(ExecutionRejectReason.ORDER_EVENT_NOT_SUPPORTED);
        command.setRejectMessage("Ignored market-order lifecycle event: " + eventName + " " + scope);
        return command;
    }

    private ExecutionCommandEntity newBaseCommand(
            Long masterAccountId,
            FollowerRouteCacheItem follower,
            NormalizedMt5Signal signal,
            String masterSymbol,
            String followerSymbol
    ) {
        ExecutionCommandEntity command = new ExecutionCommandEntity();
        command.setMasterEventId(signal.getEventId());
        command.setMasterAccountId(masterAccountId);
        command.setMasterAccountKey(signal.getMasterAccountKey());
        command.setFollowerAccountId(follower.getFollowerAccountId());
        command.setMasterSymbol(masterSymbol);
        command.setSymbol(followerSymbol);
        command.setCopyMode(follower.getCopyMode());
        command.setSignalTime(signal.getSourceTimestamp());
        return command;
    }

    private void createDispatchOutboxIfNeeded(
            ExecutionCommandEntity command,
            NormalizedMt5Signal signal,
            FollowerRouteCacheItem follower
    ) {
        if (command.getStatus() != ExecutionCommandStatus.READY) {
            return;
        }
        if (followerDispatchOutboxRepository.existsByExecutionCommandId(command.getId())) {
            return;
        }

        FollowerDispatchOutboxEntity outbox = new FollowerDispatchOutboxEntity();
        outbox.setExecutionCommandId(command.getId());
        outbox.setMasterEventId(command.getMasterEventId());
        outbox.setFollowerAccountId(command.getFollowerAccountId());
        outbox.setStatus(FollowerDispatchStatus.PENDING);
        outbox.setPayloadJson(buildDispatchPayload(command, signal, follower));
        FollowerDispatchOutboxEntity saved = followerDispatchOutboxRepository.save(outbox);
        applicationEventPublisher.publishEvent(new FollowerDispatchCreatedEvent(saved.getId(), saved.getFollowerAccountId()));
    }

    private Rejection validateAndComputeDealVolume(
            CopyMode copyMode,
            FollowerRiskCacheSnapshot risk,
            String symbol,
            BigDecimal masterVolume,
            JsonNode signalPayload,
            Long followerAccountId
    ) {
        Rejection symbolValidation = validateSymbolAccess(risk, symbol);
        if (symbolValidation.isRejected()) {
            return symbolValidation;
        }
        if (masterVolume == null || masterVolume.compareTo(BigDecimal.ZERO) <= 0) {
            return Rejection.rejected(ExecutionRejectReason.INVALID_VOLUME, "Master volume is missing or invalid", masterVolume);
        }

        BigDecimal requestedVolume;
        switch (copyMode) {
            case FIXED_LOT:
                if (risk.getFixedLot() == null || risk.getFixedLot().compareTo(BigDecimal.ZERO) <= 0) {
                    return Rejection.rejected(ExecutionRejectReason.FIXED_LOT_MISSING, "Fixed lot is not configured", null);
                }
                requestedVolume = risk.getFixedLot();
                break;
            case BALANCE_RATIO:
            case EQUITY_RATIO:
                BigDecimal configuredRiskRatio = resolveConfiguredRiskRatio(risk);
                if (configuredRiskRatio == null) {
                    return Rejection.rejected(ExecutionRejectReason.RATIO_MISSING, "Configured ratio must be positive", null);
                }
                AccountScaleResolution scaleResolution = resolveAccountScale(copyMode, signalPayload, followerAccountId);
                if (!scaleResolution.ready()) {
                    return Rejection.rejected(
                            ExecutionRejectReason.ACCOUNT_FUNDS_UNAVAILABLE,
                            scaleResolution.message(),
                            null
                    );
                }
                requestedVolume = masterVolume
                        .multiply(configuredRiskRatio)
                        .multiply(scaleResolution.scaleRatio());
                break;
            case FOLLOW_MASTER:
                requestedVolume = masterVolume;
                break;
            default:
                return Rejection.rejected(ExecutionRejectReason.UNSUPPORTED_SIGNAL, "Unsupported copy mode", null);
        }

        requestedVolume = requestedVolume.setScale(4, RoundingMode.HALF_UP);
        if (requestedVolume.compareTo(BigDecimal.ZERO) <= 0) {
            return Rejection.rejected(ExecutionRejectReason.INVALID_VOLUME, "Requested volume is invalid", requestedVolume);
        }
        if (risk.getMaxLot() != null && requestedVolume.compareTo(risk.getMaxLot()) > 0) {
            return Rejection.rejected(ExecutionRejectReason.MAX_LOT_EXCEEDED, "Requested volume exceeds max lot", requestedVolume);
        }

        return Rejection.ready(requestedVolume);
    }

    private Rejection validateOrderSignal(
            CopyMode copyMode,
            FollowerRiskCacheSnapshot risk,
            String symbol,
            String eventName,
            String scope,
            boolean pendingOrder,
            boolean marketOrder,
            BigDecimal masterVolume,
            JsonNode signalPayload,
            Long followerAccountId
    ) {
        Rejection symbolValidation = validateSymbolAccess(risk, symbol);
        if (symbolValidation.isRejected()) {
            return symbolValidation;
        }

        OrderInstruction instruction = resolveOrderInstruction(eventName, pendingOrder, marketOrder);
        if (!instruction.supported()) {
            return Rejection.rejected(
                    ExecutionRejectReason.ORDER_EVENT_NOT_SUPPORTED,
                    "Unsupported order event for the current phase",
                    null
            );
        }

        if (instruction.commandType() == ExecutionCommandType.SYNC_TP_SL) {
            if (!risk.isFollowTpSl()) {
                return Rejection.rejected(
                        ExecutionRejectReason.FOLLOW_TP_SL_DISABLED,
                        "Follower risk rule does not allow TP/SL sync",
                        null
                );
            }
            if (!"ACTIVE".equalsIgnoreCase(scope)) {
                return Rejection.rejected(
                        ExecutionRejectReason.ORDER_EVENT_NOT_SUPPORTED,
                        "Position TP/SL sync only supports ACTIVE order updates",
                        null
                );
            }
            return Rejection.ready(null);
        }

        if (instruction.commandType() == ExecutionCommandType.CANCEL_PENDING_ORDER) {
            return Rejection.ready(null);
        }

        Rejection volumeValidation = validateAndComputeDealVolume(
                copyMode,
                risk,
                symbol,
                masterVolume,
                signalPayload,
                followerAccountId
        );
        if (volumeValidation.isRejected()) {
            return volumeValidation;
        }
        if ("ORDER_ADD".equalsIgnoreCase(eventName) && !"ACTIVE".equalsIgnoreCase(scope)) {
            return Rejection.rejected(
                    ExecutionRejectReason.ORDER_EVENT_NOT_SUPPORTED,
                    "Pending ORDER_ADD must be ACTIVE",
                    null
            );
        }
        if ("ORDER_UPDATE".equalsIgnoreCase(eventName) && !"ACTIVE".equalsIgnoreCase(scope)) {
            return Rejection.rejected(
                    ExecutionRejectReason.ORDER_EVENT_NOT_SUPPORTED,
                    "Pending ORDER_UPDATE must be ACTIVE",
                    null
            );
        }
        return Rejection.ready(volumeValidation.volume());
    }

    private Rejection validateSymbolAccess(FollowerRiskCacheSnapshot risk, String symbol) {
        if (!StringUtils.hasText(symbol)) {
            return Rejection.rejected(ExecutionRejectReason.UNSUPPORTED_SIGNAL, "Symbol is missing", null);
        }

        Set<String> allowedSymbols = splitSymbols(risk.getAllowedSymbols());
        if (!allowedSymbols.isEmpty() && !allowedSymbols.contains(symbol.toUpperCase())) {
            return Rejection.rejected(ExecutionRejectReason.SYMBOL_NOT_ALLOWED, "Symbol is not in allowed list", null);
        }

        Set<String> blockedSymbols = splitSymbols(risk.getBlockedSymbols());
        if (blockedSymbols.contains(symbol.toUpperCase())) {
            return Rejection.rejected(ExecutionRejectReason.SYMBOL_BLOCKED, "Symbol is blocked", null);
        }
        return Rejection.ready(null);
    }

    private Set<String> splitSymbols(String raw) {
        if (!StringUtils.hasText(raw)) {
            return Collections.emptySet();
        }
        return List.of(raw.split("[,;\\s]+")).stream()
                .filter(StringUtils::hasText)
                .map(String::toUpperCase)
                .collect(Collectors.toSet());
    }

    private ExecutionCommandResponse toResponse(ExecutionCommandEntity entity) {
        ExecutionCommandResponse response = new ExecutionCommandResponse();
        response.setId(entity.getId());
        response.setMasterEventId(entity.getMasterEventId());
        response.setMasterAccountId(entity.getMasterAccountId());
        response.setMasterAccountKey(entity.getMasterAccountKey());
        response.setFollowerAccountId(entity.getFollowerAccountId());
        response.setMasterSymbol(entity.getMasterSymbol());
        response.setSignalType(entity.getSignalType());
        response.setCommandType(entity.getCommandType());
        response.setSymbol(entity.getSymbol());
        response.setMasterAction(entity.getMasterAction());
        response.setFollowerAction(entity.getFollowerAction());
        response.setCopyMode(entity.getCopyMode());
        response.setRequestedVolume(entity.getRequestedVolume());
        response.setRequestedPrice(entity.getRequestedPrice());
        response.setRequestedSl(entity.getRequestedSl());
        response.setRequestedTp(entity.getRequestedTp());
        response.setMasterDealId(entity.getMasterDealId());
        response.setMasterOrderId(entity.getMasterOrderId());
        response.setMasterPositionId(entity.getMasterPositionId());
        response.setStatus(entity.getStatus());
        response.setRejectReason(entity.getRejectReason());
        response.setRejectMessage(entity.getRejectMessage());
        response.setSignalTime(entity.getSignalTime());
        response.setCreatedAt(entity.getCreatedAt());
        return response;
    }

    private FollowerDispatchOutboxResponse toDispatchResponse(FollowerDispatchOutboxEntity entity) {
        FollowerDispatchOutboxResponse response = new FollowerDispatchOutboxResponse();
        response.setId(entity.getId());
        response.setExecutionCommandId(entity.getExecutionCommandId());
        response.setMasterEventId(entity.getMasterEventId());
        response.setFollowerAccountId(entity.getFollowerAccountId());
        response.setStatus(entity.getStatus());
        response.setStatusMessage(entity.getStatusMessage());
        response.setPayloadJson(entity.getPayloadJson());
        response.setAckedAt(entity.getAckedAt());
        response.setFailedAt(entity.getFailedAt());
        response.setCreatedAt(entity.getCreatedAt());
        response.setUpdatedAt(entity.getUpdatedAt());
        return response;
    }

    private ExecutionTraceResponse buildTrace(
            Long masterAccountId,
            Long masterOrderId,
            Long masterPositionId,
            List<ExecutionCommandEntity> commandEntities
    ) {
        ExecutionTraceResponse response = emptyTrace(masterAccountId, masterOrderId, masterPositionId);
        if (commandEntities.isEmpty()) {
            return response;
        }

        response.setCommands(commandEntities.stream()
                .map(this::toResponse)
                .toList());

        List<Long> commandIds = commandEntities.stream()
                .map(ExecutionCommandEntity::getId)
                .toList();
        Map<Long, List<FollowerDispatchOutboxEntity>> dispatchByCommandId = followerDispatchOutboxRepository
                .findByExecutionCommandIdInOrderByIdAsc(commandIds)
                .stream()
                .collect(Collectors.groupingBy(FollowerDispatchOutboxEntity::getExecutionCommandId));

        response.setDispatches(commandIds.stream()
                .flatMap(commandId -> dispatchByCommandId.getOrDefault(commandId, Collections.emptyList()).stream())
                .map(this::toDispatchResponse)
                .toList());
        return response;
    }

    private ExecutionTraceResponse emptyTrace(Long masterAccountId, Long masterOrderId, Long masterPositionId) {
        ExecutionTraceResponse response = new ExecutionTraceResponse();
        response.setMasterAccountId(masterAccountId);
        response.setMasterOrderId(masterOrderId);
        response.setMasterPositionId(masterPositionId);
        return response;
    }

    private String buildDispatchPayload(
            ExecutionCommandEntity command,
            NormalizedMt5Signal signal,
            FollowerRouteCacheItem follower
    ) {
        DispatchSlippagePolicy slippagePolicy = dispatchSlippagePolicyResolver.resolve(signal.getPayload(), follower.getRisk());
        boolean slippageEnabled = slippagePolicy.isEnabled()
                && command.getCommandType() == ExecutionCommandType.OPEN_POSITION;
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("commandId", command.getId());
        payload.put("masterEventId", command.getMasterEventId());
        payload.put("masterAccountId", command.getMasterAccountId());
        payload.put("masterAccountKey", command.getMasterAccountKey());
        payload.put("followerAccountId", command.getFollowerAccountId());
        payload.put("signalType", command.getSignalType().name());
        payload.put("commandType", command.getCommandType().name());
        payload.put("symbol", command.getSymbol());
        putNullableString(payload, "masterSymbol", command.getMasterSymbol());
        payload.put("masterAction", command.getMasterAction());
        payload.put("followerAction", command.getFollowerAction());
        payload.put("copyMode", command.getCopyMode().name());
        putNullableLong(payload, "masterDealId", command.getMasterDealId());
        putNullableLong(payload, "masterOrderId", command.getMasterOrderId());
        putNullableLong(payload, "masterPositionId", command.getMasterPositionId());
        putNullableBigDecimal(payload, "volume", command.getRequestedVolume());
        putNullableBigDecimal(payload, "requestedPrice", command.getRequestedPrice());
        putNullableBigDecimal(payload, "requestedSl", command.getRequestedSl());
        putNullableBigDecimal(payload, "requestedTp", command.getRequestedTp());
        putNullableString(payload, "signalTime", command.getSignalTime());
        payload.put("reverseFollow", follower.getRisk().isReverseFollow());
        payload.put("followTpSl", follower.getRisk().isFollowTpSl());
        putNullableInteger(payload, "maxSlippagePoints", follower.getRisk().getMaxSlippagePoints());
        putNullableInteger(payload, "masterOrderType", readInteger(signal.getPayload(), "order_type"));
        putNullableInteger(payload, "masterOrderState", readInteger(signal.getPayload(), "order_state"));
        putNullableBigDecimal(payload, "configuredRiskRatio", resolveConfiguredRiskRatioOrDefault(command.getCopyMode(), follower.getRisk()));
        AccountScaleResolution scaleResolution = resolveAccountScale(
                command.getCopyMode(),
                signal.getPayload(),
                command.getFollowerAccountId()
        );
        putNullableBigDecimal(payload, "accountScaleRatio", scaleResolution.scaleRatio());
        putNullableBigDecimal(payload, "masterFunds", scaleResolution.masterFunds());
        putNullableBigDecimal(payload, "followerFunds", scaleResolution.followerFunds());

        ObjectNode slippageNode = payload.putObject("slippagePolicy");
        slippageNode.put("enabled", slippageEnabled);
        slippageNode.put("instrumentCategory", slippagePolicy.getInstrumentCategory().name());
        slippageNode.put("mode", slippagePolicy.getMode().name());
        putNullableBigDecimal(slippageNode, "maxPips", slippagePolicy.getMaxPips());
        putNullableBigDecimal(slippageNode, "maxPrice", slippagePolicy.getMaxPrice());
        putNullableInteger(slippageNode, "maxDeviationPoints", follower.getRisk().getMaxSlippagePoints());

        ObjectNode instrumentMeta = payload.putObject("instrumentMeta");
        putNullableString(instrumentMeta, "sourceSymbol", command.getMasterSymbol());
        payload.put("targetSymbol", command.getSymbol());
        putNullableInteger(instrumentMeta, "digits", readInteger(signal.getPayload(), "symbol_digits"));
        putNullableBigDecimal(instrumentMeta, "point", readDecimal(signal.getPayload(), "symbol_point"));
        putNullableBigDecimal(instrumentMeta, "tickSize", readDecimal(signal.getPayload(), "symbol_tick_size"));
        putNullableBigDecimal(instrumentMeta, "tickValue", readDecimal(signal.getPayload(), "symbol_tick_value"));
        putNullableBigDecimal(instrumentMeta, "contractSize", readDecimal(signal.getPayload(), "symbol_contract_size"));
        putNullableBigDecimal(instrumentMeta, "volumeStep", readDecimal(signal.getPayload(), "symbol_volume_step"));
        putNullableBigDecimal(instrumentMeta, "volumeMin", readDecimal(signal.getPayload(), "symbol_volume_min"));
        putNullableBigDecimal(instrumentMeta, "volumeMax", readDecimal(signal.getPayload(), "symbol_volume_max"));
        putNullableString(instrumentMeta, "currencyBase", readText(signal.getPayload(), "symbol_currency_base"));
        putNullableString(instrumentMeta, "currencyProfit", readText(signal.getPayload(), "symbol_currency_profit"));
        putNullableString(instrumentMeta, "currencyMargin", readText(signal.getPayload(), "symbol_currency_margin"));

        ObjectNode masterSignal = payload.putObject("masterSignal");
        putNullableLong(masterSignal, "login", signal.getLogin());
        putNullableString(masterSignal, "server", signal.getServer());
        putNullableString(masterSignal, "symbol", command.getMasterSymbol());
        putNullableLong(masterSignal, "deal", readLong(signal.getPayload(), "deal"));
        putNullableLong(masterSignal, "order", readLong(signal.getPayload(), "order"));
        putNullableLong(masterSignal, "position", readLong(signal.getPayload(), "position"));
        putNullableString(masterSignal, "event", readText(signal.getPayload(), "event"));
        putNullableString(masterSignal, "scope", readText(signal.getPayload(), "scope"));
        putNullableInteger(masterSignal, "orderType", readInteger(signal.getPayload(), "order_type"));
        putNullableInteger(masterSignal, "orderState", readInteger(signal.getPayload(), "order_state"));
        putNullableBigDecimal(masterSignal, "price", readDecimal(signal.getPayload(), "price"));
        putNullableBigDecimal(masterSignal, "priceOpen", readDecimal(signal.getPayload(), "price_open"));
        putNullableBigDecimal(masterSignal, "sl", readDecimal(signal.getPayload(), "sl"));
        putNullableBigDecimal(masterSignal, "tp", readDecimal(signal.getPayload(), "tp"));
        putNullableBigDecimal(masterSignal, "volInit", readDecimal(signal.getPayload(), "vol_init"));
        putNullableBigDecimal(masterSignal, "volCur", readDecimal(signal.getPayload(), "vol_cur"));
        putNullableBigDecimal(masterSignal, "accountBalance", readDecimal(signal.getPayload(), "account_balance"));
        putNullableBigDecimal(masterSignal, "accountEquity", readDecimal(signal.getPayload(), "account_equity"));
        putNullableBigDecimal(masterSignal, "positionVolumeBefore", readDecimal(signal.getPayload(), "position_volume_before"));
        putNullableBigDecimal(masterSignal, "positionVolumeAfter", readDecimal(signal.getPayload(), "position_volume_after"));
        putNullableString(masterSignal, "comment", readText(signal.getPayload(), "comment"));
        putNullableLong(masterSignal, "magic", readLong(signal.getPayload(), "magic"));

        if (command.getCommandType() == ExecutionCommandType.CLOSE_POSITION) {
            putNullableBigDecimal(payload, "closeRatio", resolveCloseRatio(signal.getPayload()));
            payload.put("closeAll", isCloseAll(signal.getPayload()));
        }

        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize follower dispatch payload", ex);
        }
    }

    private String resolveFollowerAction(String masterAction, boolean reverseFollow) {
        if (!StringUtils.hasText(masterAction) || !reverseFollow) {
            return masterAction;
        }
        String normalized = masterAction.trim().toUpperCase();
        if (normalized.startsWith("BUY ")) {
            return "SELL " + normalized.substring(4);
        }
        if (normalized.startsWith("SELL ")) {
            return "BUY " + normalized.substring(5);
        }
        return normalized;
    }

    private ExecutionCommandType resolveDealCommandType(String action) {
        if (!StringUtils.hasText(action)) {
            return ExecutionCommandType.OPEN_POSITION;
        }
        String normalized = action.trim().toUpperCase();
        if (normalized.contains("CLOSE")) {
            return ExecutionCommandType.CLOSE_POSITION;
        }
        return ExecutionCommandType.OPEN_POSITION;
    }

    private OrderInstruction resolveOrderInstruction(String eventName, boolean pendingOrder, boolean marketOrder) {
        String normalized = StringUtils.hasText(eventName) ? eventName.trim().toUpperCase() : "";
        if (pendingOrder) {
            switch (normalized) {
                case "ORDER_ADD":
                    return new OrderInstruction(true, ExecutionCommandType.CREATE_PENDING_ORDER, "CREATE_PENDING_ORDER");
                case "ORDER_UPDATE":
                    return new OrderInstruction(true, ExecutionCommandType.UPDATE_PENDING_ORDER, "UPDATE_PENDING_ORDER");
                case "ORDER_DELETE":
                    return new OrderInstruction(true, ExecutionCommandType.CANCEL_PENDING_ORDER, "CANCEL_PENDING_ORDER");
                default:
                    return new OrderInstruction(false, ExecutionCommandType.UPDATE_PENDING_ORDER, "UNSUPPORTED");
            }
        }
        if (marketOrder && "ORDER_UPDATE".equals(normalized)) {
            return new OrderInstruction(true, ExecutionCommandType.SYNC_TP_SL, "SYNC_TP_SL");
        }
        return new OrderInstruction(false, ExecutionCommandType.UPDATE_PENDING_ORDER, "UNSUPPORTED");
    }

    private boolean isPendingOrder(Integer orderType) {
        return orderType != null && orderType >= 2;
    }

    private boolean isMarketOrder(Integer orderType) {
        return orderType != null && (orderType == 0 || orderType == 1);
    }

    private BigDecimal resolveRequestedOrderVolume(JsonNode payload, boolean pendingOrder) {
        if (!pendingOrder) {
            return readDecimal(payload, "vol_cur");
        }
        BigDecimal currentVolume = readDecimal(payload, "vol_cur");
        if (currentVolume != null && currentVolume.compareTo(BigDecimal.ZERO) > 0) {
            return currentVolume;
        }
        return readDecimal(payload, "vol_init");
    }

    private Long readLong(JsonNode payload, String field) {
        return payload.hasNonNull(field) ? payload.path(field).asLong() : null;
    }

    private Integer readInteger(JsonNode payload, String field) {
        return payload.hasNonNull(field) ? payload.path(field).asInt() : null;
    }

    private BigDecimal readDecimal(JsonNode payload, String field) {
        return payload.hasNonNull(field) ? payload.path(field).decimalValue() : null;
    }

    private BigDecimal resolveConfiguredRiskRatio(FollowerRiskCacheSnapshot risk) {
        if (risk.getBalanceRatio() == null) {
            return BigDecimal.ONE;
        }
        return risk.getBalanceRatio().compareTo(BigDecimal.ZERO) > 0 ? risk.getBalanceRatio() : null;
    }

    private BigDecimal resolveConfiguredRiskRatioOrDefault(CopyMode copyMode, FollowerRiskCacheSnapshot risk) {
        if (copyMode != CopyMode.BALANCE_RATIO && copyMode != CopyMode.EQUITY_RATIO) {
            return null;
        }
        return resolveConfiguredRiskRatio(risk);
    }

    private BigDecimal resolveAccountScaleRatio(CopyMode copyMode, JsonNode signalPayload, Long followerAccountId) {
        return resolveAccountScale(copyMode, signalPayload, followerAccountId).scaleRatio();
    }

    private BigDecimal resolveMasterFunds(CopyMode copyMode, JsonNode signalPayload) {
        if (copyMode == CopyMode.BALANCE_RATIO) {
            return readDecimal(signalPayload, "account_balance");
        }
        if (copyMode == CopyMode.EQUITY_RATIO) {
            return readDecimal(signalPayload, "account_equity");
        }
        return null;
    }

    private BigDecimal resolveFollowerFunds(CopyMode copyMode, Long followerAccountId) {
        if (followerAccountId == null) {
            return null;
        }
        return resolveFollowerRuntimeState(followerAccountId)
                .map(state -> extractFollowerFunds(copyMode, state))
                .orElse(null);
    }

    private AccountScaleResolution resolveAccountScale(CopyMode copyMode, JsonNode signalPayload, Long followerAccountId) {
        if (copyMode != CopyMode.BALANCE_RATIO && copyMode != CopyMode.EQUITY_RATIO) {
            return AccountScaleResolution.notRequired();
        }

        BigDecimal masterFunds = resolveMasterFunds(copyMode, signalPayload);
        if (masterFunds == null || masterFunds.compareTo(BigDecimal.ZERO) <= 0) {
            return AccountScaleResolution.unavailable(masterFundsLabel(copyMode) + " is missing or invalid on master signal");
        }

        if (followerAccountId == null) {
            return AccountScaleResolution.unavailable("Follower account id is missing for ratio-based scaling");
        }

        Optional<Mt5AccountRuntimeStateSnapshot> followerState = runtimeStateStore.findByAccountId(followerAccountId);
        if (!followerState.isPresent()) {
            return AccountScaleResolution.unavailable(
                    followerFundsLabel(copyMode) + " snapshot is missing for follower account " + followerAccountId
            );
        }
        if (!runtimeStateStore.isFreshForFunds(followerState.get())) {
            return AccountScaleResolution.unavailable(
                    followerFundsLabel(copyMode) + " snapshot is stale for follower account " + followerAccountId
            );
        }

        BigDecimal followerFunds = extractFollowerFunds(copyMode, followerState.get());
        if (followerFunds == null || followerFunds.compareTo(BigDecimal.ZERO) <= 0) {
            return AccountScaleResolution.unavailable(
                    followerFundsLabel(copyMode) + " is missing or invalid on follower runtime-state"
            );
        }

        return AccountScaleResolution.ready(
                followerFunds.divide(masterFunds, 8, RoundingMode.HALF_UP),
                masterFunds,
                followerFunds
        );
    }

    private Optional<Mt5AccountRuntimeStateSnapshot> resolveFollowerRuntimeState(Long followerAccountId) {
        return runtimeStateStore.findFreshByAccountId(followerAccountId);
    }

    private BigDecimal extractFollowerFunds(CopyMode copyMode, Mt5AccountRuntimeStateSnapshot state) {
        if (state == null) {
            return null;
        }
        if (copyMode == CopyMode.BALANCE_RATIO) {
            return state.getBalance();
        }
        if (copyMode == CopyMode.EQUITY_RATIO) {
            return state.getEquity();
        }
        return null;
    }

    private String masterFundsLabel(CopyMode copyMode) {
        if (copyMode == CopyMode.EQUITY_RATIO) {
            return "Master equity";
        }
        return "Master balance";
    }

    private String followerFundsLabel(CopyMode copyMode) {
        if (copyMode == CopyMode.EQUITY_RATIO) {
            return "Follower equity";
        }
        return "Follower balance";
    }

    private BigDecimal resolveCloseRatio(JsonNode signalPayload) {
        BigDecimal positionVolumeBefore = readDecimal(signalPayload, "position_volume_before");
        BigDecimal closeVolume = readDecimal(signalPayload, "volume");
        if (positionVolumeBefore == null
                || closeVolume == null
                || positionVolumeBefore.compareTo(BigDecimal.ZERO) <= 0
                || closeVolume.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        BigDecimal ratio = closeVolume.divide(positionVolumeBefore, 8, RoundingMode.HALF_UP);
        return ratio.compareTo(BigDecimal.ONE) > 0 ? BigDecimal.ONE : ratio;
    }

    private boolean isCloseAll(JsonNode signalPayload) {
        BigDecimal positionVolumeAfter = readDecimal(signalPayload, "position_volume_after");
        if (positionVolumeAfter != null) {
            return positionVolumeAfter.compareTo(BigDecimal.ZERO) <= 0;
        }

        BigDecimal positionVolumeBefore = readDecimal(signalPayload, "position_volume_before");
        BigDecimal closeVolume = readDecimal(signalPayload, "volume");
        return positionVolumeBefore != null
                && closeVolume != null
                && closeVolume.compareTo(positionVolumeBefore) >= 0;
    }

    private String readText(JsonNode payload, String field) {
        return payload.hasNonNull(field) ? payload.path(field).asText() : null;
    }

    private void putNullableString(ObjectNode target, String field, String value) {
        if (StringUtils.hasText(value)) {
            target.put(field, value);
        } else {
            target.putNull(field);
        }
    }

    private String resolveFollowerSymbol(FollowerRiskCacheSnapshot risk, String masterSymbol) {
        if (!StringUtils.hasText(masterSymbol)) {
            return masterSymbol;
        }
        if (risk == null || risk.getSymbolMappings() == null || risk.getSymbolMappings().isEmpty()) {
            return masterSymbol;
        }

        String lookupKey = masterSymbol.trim().toUpperCase(Locale.ROOT);
        String mapped = risk.getSymbolMappings().get(lookupKey);
        return StringUtils.hasText(mapped) ? mapped : masterSymbol;
    }

    private void putNullableLong(ObjectNode target, String field, Long value) {
        if (value != null) {
            target.put(field, value);
        } else {
            target.putNull(field);
        }
    }

    private void putNullableInteger(ObjectNode target, String field, Integer value) {
        if (value != null) {
            target.put(field, value);
        } else {
            target.putNull(field);
        }
    }

    private void putNullableBigDecimal(ObjectNode target, String field, BigDecimal value) {
        if (value != null) {
            target.put(field, value);
        } else {
            target.putNull(field);
        }
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private static final class OrderInstruction {
        private final boolean supported;
        private final ExecutionCommandType commandType;
        private final String followerAction;

        private OrderInstruction(boolean supported, ExecutionCommandType commandType, String followerAction) {
            this.supported = supported;
            this.commandType = commandType;
            this.followerAction = followerAction;
        }

        boolean supported() {
            return supported;
        }

        ExecutionCommandType commandType() {
            return commandType;
        }

        String followerAction() {
            return followerAction;
        }
    }

    private static final class AccountScaleResolution {
        private final boolean ready;
        private final BigDecimal scaleRatio;
        private final BigDecimal masterFunds;
        private final BigDecimal followerFunds;
        private final String message;

        private AccountScaleResolution(
                boolean ready,
                BigDecimal scaleRatio,
                BigDecimal masterFunds,
                BigDecimal followerFunds,
                String message
        ) {
            this.ready = ready;
            this.scaleRatio = scaleRatio;
            this.masterFunds = masterFunds;
            this.followerFunds = followerFunds;
            this.message = message;
        }

        static AccountScaleResolution notRequired() {
            return new AccountScaleResolution(true, null, null, null, null);
        }

        static AccountScaleResolution ready(BigDecimal scaleRatio, BigDecimal masterFunds, BigDecimal followerFunds) {
            return new AccountScaleResolution(true, scaleRatio, masterFunds, followerFunds, null);
        }

        static AccountScaleResolution unavailable(String message) {
            return new AccountScaleResolution(false, null, null, null, message);
        }

        boolean ready() {
            return ready;
        }

        BigDecimal scaleRatio() {
            return scaleRatio;
        }

        BigDecimal masterFunds() {
            return masterFunds;
        }

        BigDecimal followerFunds() {
            return followerFunds;
        }

        String message() {
            return message;
        }
    }

    private static final class Rejection {
        private final boolean rejected;
        private final ExecutionRejectReason reason;
        private final String message;
        private final BigDecimal volume;

        private Rejection(boolean rejected, ExecutionRejectReason reason, String message, BigDecimal volume) {
            this.rejected = rejected;
            this.reason = reason;
            this.message = message;
            this.volume = volume;
        }

        static Rejection ready(BigDecimal volume) {
            return new Rejection(false, null, null, volume);
        }

        static Rejection rejected(ExecutionRejectReason reason, String message, BigDecimal volume) {
            return new Rejection(true, reason, message, volume);
        }

        boolean isRejected() {
            return rejected;
        }

        ExecutionRejectReason reason() {
            return reason;
        }

        String message() {
            return message;
        }

        BigDecimal volume() {
            return volume;
        }
    }
}
