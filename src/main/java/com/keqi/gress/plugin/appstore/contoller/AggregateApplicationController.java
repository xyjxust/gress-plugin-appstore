package com.keqi.gress.plugin.appstore.contoller;

import com.keqi.gress.common.model.Result;
import com.keqi.gress.plugin.api.ui.annotation.PluginAction;
import com.keqi.gress.plugin.api.ui.annotation.PluginMenu;
import com.keqi.gress.plugin.appstore.dto.AggregateApplicationRequest;
import com.keqi.gress.plugin.appstore.dto.ApplicationDTO;
import com.keqi.gress.plugin.appstore.service.ApplicationManagementService;
import com.keqi.gress.plugin.appstore.support.OperatorContextHelper;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 聚合应用 API（与 plugin.yml → plugin.ui.menus 中 id = aggregate-applications 对应）
 */
@Slf4j
@Service
@RestController
@RequestMapping("/applications/aggregates")
@Valid
@PluginMenu(id = "aggregate-applications", name = "聚合应用管理", managementEnabled = true)
public class AggregateApplicationController {

    @Autowired
    private ApplicationManagementService applicationManagementService;

    @GetMapping
    @PluginAction(id = "refresh", name = "刷新列表")
    public Result<List<ApplicationDTO>> listAggregates() {
        return applicationManagementService.listAggregateApplications();
    }

    @GetMapping("/available-plugins")
    public Result<List<ApplicationDTO>> listAggregatablePlugins() {
        return applicationManagementService.listAggregatablePlugins();
    }

    @PostMapping
    @PluginAction(id = "save", name = "保存聚合应用")
    public Result<Void> createAggregate(@RequestBody AggregateApplicationRequest request) {
        return applicationManagementService.createAggregateApplication(
                request, OperatorContextHelper.getOperatorName());
    }

    @PutMapping("/{id}")
    @PluginAction(id = "update", name = "更新聚合应用", managementEnabled = true, actionCode = "UPDATE")
    public Result<Void> updateAggregate(@PathVariable Long id, @RequestBody AggregateApplicationRequest request) {
        return applicationManagementService.updateAggregateApplication(
                id, request, OperatorContextHelper.getOperatorName());
    }

    @DeleteMapping("/{id}")
    @PluginAction(id = "delete", name = "删除聚合应用", managementEnabled = true, actionCode = "DELETE")
    public Result<Void> deleteAggregate(@PathVariable Long id) {
        return applicationManagementService.deleteAggregateApplication(
                id, OperatorContextHelper.getOperatorName());
    }
}
