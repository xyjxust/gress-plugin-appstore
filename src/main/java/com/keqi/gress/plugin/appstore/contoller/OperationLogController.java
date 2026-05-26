package com.keqi.gress.plugin.appstore.contoller;

import com.keqi.gress.common.model.Result;
import com.keqi.gress.plugin.api.ui.annotation.PluginAction;
import com.keqi.gress.plugin.api.ui.annotation.PluginMenu;
import com.keqi.gress.plugin.appstore.dto.ApplicationOperationLogDTO;
import com.keqi.gress.plugin.appstore.dto.PageResult;
import com.keqi.gress.plugin.appstore.service.ApplicationManagementService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 应用操作日志 API（与 plugin.yml → plugin.ui.menus 中 id = operation-logs 对应）
 */
@Slf4j
@Service
@RestController
@RequestMapping("/applications")
@Valid
@PluginMenu(id = "operation-logs", name = "操作日志")
public class OperationLogController {

    @Autowired
    private ApplicationManagementService applicationManagementService;

    @GetMapping("/operation-logs")
    @PluginAction(id = "refresh", name = "刷新")
    public Result<PageResult<ApplicationOperationLogDTO>> getAllOperationLogs(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer size,
            @RequestParam(required = false) String operationType,
            @RequestParam(required = false) String operatorName,
            @RequestParam(required = false) String applicationName,
            @RequestParam(required = false) String status) {

        log.info("查询所有应用操作日志: page={}, size={}, operationType={}, operatorName={}, applicationName={}, status={}",
                page, size, operationType, operatorName, applicationName, status);
        return applicationManagementService.getAllOperationLogs(
                page, size, operationType, operatorName, applicationName, status);
    }

    @GetMapping("/{id}/operation-logs")
    @PluginAction(id = "view-detail", name = "查看详情", managementEnabled = true, actionCode = "VIEW")
    public Result<PageResult<ApplicationOperationLogDTO>> getApplicationOperationLogs(
            @PathVariable Long id,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer size,
            @RequestParam(required = false) String operationType) {

        log.info("查询应用操作日志: id={}, page={}, size={}, operationType={}", id, page, size, operationType);
        return applicationManagementService.getApplicationOperationLogs(id, page, size, operationType);
    }
}
