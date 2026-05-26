package com.keqi.gress.plugin.appstore.contoller;

import com.keqi.gress.common.model.Result;
import com.keqi.gress.plugin.api.ui.annotation.PluginAction;
import com.keqi.gress.plugin.api.ui.annotation.PluginMenu;
import com.keqi.gress.plugin.appstore.dao.StackConfigDao;
import com.keqi.gress.plugin.appstore.dao.StackDeploymentDao;
import com.keqi.gress.plugin.appstore.dao.StackDeploymentLogDao;
import com.keqi.gress.plugin.appstore.dao.StackTargetDao;
import com.keqi.gress.plugin.appstore.domain.entity.StackConfigEntity;
import com.keqi.gress.plugin.appstore.domain.entity.StackDeploymentEntity;
import com.keqi.gress.plugin.appstore.domain.entity.StackDeploymentLogEntity;
import com.keqi.gress.plugin.appstore.domain.entity.StackTargetEntity;
import com.keqi.gress.plugin.appstore.service.deploy.StackDeploymentService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 多套/多节点部署（MVP）
 *
 * 说明：
 * - 先落地数据模型与管理接口（Stack/Target/Deployment）
 * - 真正的“远程执行、分发、nginx 渲染”在后续迭代中逐步完善
 */
@Service
@RestController
@RequestMapping("/stack-deploy")
@Valid
@Slf4j
@PluginMenu(id = "stack-deploy", name = "多套部署", managementEnabled = true)
public class StackDeploymentController {

    @Autowired(required = false)
    private StackConfigDao stackConfigDao;

    @Autowired(required = false)
    private StackTargetDao stackTargetDao;

    @Autowired(required = false)
    private StackDeploymentDao stackDeploymentDao;

    @Autowired(required = false)
    private StackDeploymentLogDao stackDeploymentLogDao;

    @Autowired(required = false)
    private StackDeploymentService stackDeploymentService;

    // ---------------- Stack Config ----------------

    @GetMapping("/stacks")
    @PluginAction(id = "stacks", name = "Stack 管理")
    public Result<List<StackConfigEntity>> listStacks() {
        if (stackConfigDao == null) return Result.success(List.of());
        return Result.success(stackConfigDao.listAll());
    }

    @GetMapping("/stacks/{stackId}")
    public Result<StackConfigEntity> getStack(@PathVariable String stackId) {
        if (stackConfigDao == null) return Result.error("StackConfig 服务不可用");
        return stackConfigDao.findByStackId(stackId)
            .map(Result::success)
            .orElseGet(() -> Result.error("stack 不存在: " + stackId));
    }

    @PostMapping("/stacks")
    public Result<StackConfigEntity> createStack(@RequestBody StackConfigEntity entity) {
        if (stackConfigDao == null) return Result.error("StackConfig 服务不可用");
        if (entity == null || entity.getStackId() == null || entity.getStackId().isBlank()) {
            return Result.error("stackId 不能为空");
        }
        Optional<StackConfigEntity> exists = stackConfigDao.findByStackId(entity.getStackId());
        if (exists.isPresent()) {
            return Result.error("stack 已存在: " + entity.getStackId());
        }
        if (entity.getEnabled() == null) entity.setEnabled(true);
        if (entity.getDeployFronted() == null) entity.setDeployFronted(true);
        if (entity.getJoinNginx() == null) entity.setJoinNginx(true);
        stackConfigDao.save(entity);
        return Result.success(entity);
    }

    @PutMapping("/stacks/{stackId}")
    public Result<StackConfigEntity> updateStack(@PathVariable String stackId, @RequestBody StackConfigEntity body) {
        if (stackConfigDao == null) return Result.error("StackConfig 服务不可用");
        StackConfigEntity existing = stackConfigDao.findByStackId(stackId)
            .orElse(null);
        if (existing == null) return Result.error("stack 不存在: " + stackId);

        body.setId(existing.getId());
        body.setStackId(existing.getStackId());
        boolean ok = stackConfigDao.updateById(body);
        if (!ok) return Result.error("更新失败");
        return Result.success(body);
    }

    @DeleteMapping("/stacks/{stackId}")
    public Result<Void> deleteStack(@PathVariable String stackId) {
        if (stackConfigDao == null) return Result.error("StackConfig 服务不可用");
        boolean ok = stackConfigDao.deleteByStackId(stackId);
        return ok ? Result.success(null) : Result.error("删除失败或不存在");
    }

    // ---------------- Stack Targets ----------------

    @GetMapping("/stacks/{stackId}/targets")
    @PluginAction(id = "targets", name = "Target 管理", managementEnabled = true, actionCode = "VIEW")
    public Result<List<StackTargetEntity>> listTargets(@PathVariable String stackId) {
        if (stackTargetDao == null) return Result.success(List.of());
        return Result.success(stackTargetDao.listByStackId(stackId));
    }

    @PostMapping("/stacks/{stackId}/targets")
    public Result<StackTargetEntity> upsertTarget(@PathVariable String stackId, @RequestBody StackTargetEntity body) {
        if (stackTargetDao == null) return Result.error("StackTarget 服务不可用");
        if (body == null || body.getNodeId() == null || body.getNodeId().isBlank()) {
            return Result.error("nodeId 不能为空");
        }
        StackTargetEntity existing = stackTargetDao.findByStackIdAndNodeId(stackId, body.getNodeId()).orElse(null);
        if (existing == null) {
            body.setStackId(stackId);
            if (body.getEnabled() == null) body.setEnabled(true);
            if (body.getRoles() == null || body.getRoles().isBlank()) body.setRoles("web");
            if (body.getHealthStatus() == null || body.getHealthStatus().isBlank()) body.setHealthStatus("UNKNOWN");
            stackTargetDao.save(body);
            return Result.success(body);
        }
        body.setId(existing.getId());
        body.setStackId(existing.getStackId());
        body.setNodeId(existing.getNodeId());
        boolean ok = stackTargetDao.updateById(body);
        return ok ? Result.success(body) : Result.error("更新失败");
    }

    @DeleteMapping("/stacks/{stackId}/targets/{nodeId}")
    public Result<Void> deleteTarget(@PathVariable String stackId, @PathVariable String nodeId) {
        if (stackTargetDao == null) return Result.error("StackTarget 服务不可用");
        boolean ok = stackTargetDao.deleteByStackIdAndNodeId(stackId, nodeId);
        return ok ? Result.success(null) : Result.error("删除失败或不存在");
    }

    // ---------------- Deployment ----------------

    public static class CreateDeploymentRequest {
        public String requestedVersion;
        public String mode; // SINGLE_NODE / CLUSTER
        public String strategy; // ALL_AT_ONCE / ROLLING
        public Boolean deployFronted;
        public Boolean joinNginx;
    }

    @PostMapping("/stacks/{stackId}/deployments")
    @PluginAction(id = "deploy", name = "发起部署", managementEnabled = true, actionCode = "MANAGE")
    public Result<StackDeploymentEntity> createDeployment(@PathVariable String stackId,
                                                         @RequestBody(required = false) CreateDeploymentRequest req) {
        if (stackDeploymentDao == null || stackConfigDao == null || stackTargetDao == null) {
            return Result.error("部署服务不可用");
        }
        if (stackDeploymentService == null) {
            return Result.error("StackDeploymentService 未注入");
        }

        StackConfigEntity stack = stackConfigDao.findByStackId(stackId).orElse(null);
        if (stack == null) return Result.error("stack 不存在: " + stackId);

        List<StackTargetEntity> targets = stackTargetDao.listByStackId(stackId);
        if (targets.isEmpty()) return Result.error("未绑定任何目标节点");

        StackDeploymentEntity dep = new StackDeploymentEntity();
        dep.setDeploymentId(UUID.randomUUID().toString().replace("-", ""));
        dep.setStackId(stackId);
        dep.setRequestedVersion(req != null ? req.requestedVersion : null);
        dep.setMode(req != null && req.mode != null ? req.mode : "SINGLE_NODE");
        dep.setStrategy(req != null && req.strategy != null ? req.strategy : "ALL_AT_ONCE");
        dep.setDeployFronted(req != null && req.deployFronted != null ? req.deployFronted : stack.getDeployFronted());
        dep.setJoinNginx(req != null && req.joinNginx != null ? req.joinNginx : stack.getJoinNginx());
        dep.setStatus("PENDING");
        dep.setStartedAt(null);
        dep.setEndedAt(null);
        stackDeploymentDao.save(dep);

        appendLog(dep.getDeploymentId(), null, "CREATE", "SUCCESS", "deployment created");
        stackDeploymentService.startAsync(dep.getDeploymentId());
        return Result.success(dep);
    }

    @GetMapping("/deployments/{deploymentId}")
    public Result<StackDeploymentEntity> getDeployment(@PathVariable String deploymentId) {
        if (stackDeploymentDao == null) return Result.error("部署服务不可用");
        return stackDeploymentDao.findByDeploymentId(deploymentId)
            .map(Result::success)
            .orElseGet(() -> Result.error("deployment 不存在: " + deploymentId));
    }

    @GetMapping("/deployments/{deploymentId}/logs")
    public Result<List<StackDeploymentLogEntity>> listDeploymentLogs(@PathVariable String deploymentId,
                                                                    @RequestParam(required = false, defaultValue = "200") Integer limit) {
        if (stackDeploymentLogDao == null) return Result.success(List.of());
        return Result.success(stackDeploymentLogDao.listByDeploymentId(deploymentId, limit != null ? limit : 200));
    }

    private void appendLog(String deploymentId, String nodeId, String step, String status, String output) {
        if (stackDeploymentLogDao == null) return;
        StackDeploymentLogEntity logEntity = new StackDeploymentLogEntity();
        logEntity.setDeploymentId(deploymentId);
        logEntity.setNodeId(nodeId);
        logEntity.setStep(step);
        logEntity.setStatus(status);
        logEntity.setOutput(output);
        logEntity.setTimestamp(System.currentTimeMillis());
        stackDeploymentLogDao.save(logEntity);
    }
}

