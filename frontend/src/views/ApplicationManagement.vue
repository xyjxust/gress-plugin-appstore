<template>
  <div class="application-management-page">
    <!-- 页面头部 -->
    <PageHeader title="应用管理" subtitle="管理已安装的应用插件">
      <template #actions>
        <HeaderActions
          :active-tab="activeTab"
          :refresh-loading="refreshLoading"
          :apps-outline="AppsOutline"
          :cloud-upload-outline="CloudUploadOutline"
          :refresh="Refresh"
          @aggregate="goToAggregateManagement"
          @upload="handleUploadClick"
          @refresh="loadData"
        />
      </template>
    </PageHeader>

    <div class="page-content">
      <!-- 标签页 -->
      <n-tabs v-model:value="activeTab" type="line" @update:value="handleTabChange">
        <n-tab-pane name="local" tab="我的应用">
          <LocalAppsTab
            v-model:filters="filters"
            v-model:show-advanced="showAdvanced"
            :basic-fields="basicFields"
            :active-type="filters.typeKey"
            :type-options="typeOptions"
            :tag-options="tagOptions"
            :loading="loading"
            :table-data="tableData"
            :pagination="pagination"
            :apps-outline="AppsOutline"
            :rocket-outline="RocketOutline"
            :time-outline="TimeOutline"
            :ellipsis-horizontal-outline="EllipsisHorizontalOutline"
            :format-date-time="formatDateTime"
            :get-application-type-color="getApplicationTypeColor"
            :get-plugin-types="getPluginTypes"
            :get-plugin-type-color="getPluginTypeColor"
            :get-plugin-type-text="getPluginTypeText"
            :get-more-actions="getMoreActions"
            :local-app-icon-modifier-class="localAppIconModifierClass"
            :use-aggregate-icon-img="useAggregateIconImg"
            :local-app-icon-component="localAppIconComponent"
            @search="handleSearch"
            @reset="handleReset"
            @select-type="handleSelectType"
            @select-tag="handleSelectTag"
            @view-detail="handleViewDetail"
            @show-upgrade-info="handleShowUpgradeInfo"
            @show-config="handleShowConfig"
            @upgrade="handleUpgrade"
            @restart="handleRestart"
            @more-action="handleMoreAction"
            @page-change="handlePageChange"
            @page-size-change="handlePageSizeChange"
          />
        </n-tab-pane>

        <n-tab-pane name="remote" tab="应用商店">
          <RemoteDetailInline
            v-if="remoteDetailVisible"
            v-model:active-tab="remoteDetailTab"
            :detail="remoteDetail"
            :loading="remoteDetailLoading"
            :installing="remoteDetailInstalling"
            :upgrading="remoteDetailUpgrading"
            :versions="remoteDetailVersions"
            :versions-loading="remoteDetailVersionsLoading"
            :selected-version="remoteDetailSelectedVersion"
            :chevron-back-outline="ChevronBackOutline"
            :extension-puzzle-outline="ExtensionPuzzleOutline"
            @back="closeRemoteDetail"
            @install="handleInstallRemote"
            @upgrade="handleUpgradeRemote"
            @select-version="handleSelectRemoteDetailVersion"
            @download="downloadRemoteVersion"
          />
          <RemoteStoreTab
            v-else
            v-model:remote-filters="remoteFilters"
            v-model:show-remote-advanced="showRemoteAdvanced"
            :remote-basic-fields="remoteBasicFields"
            :remote-loading="remoteLoading"
            :remote-table-data="remoteTableData"
            :remote-pagination="remotePagination"
            :type-options="typeOptions"
            :tag-options="tagOptions"
            :apps-outline="AppsOutline"
            :extension-puzzle-outline="ExtensionPuzzleOutline"
            :time-outline="TimeOutline"
            :format-date-time="formatDateTime"
            :install-remote-loading="installRemoteLoading"
            :upgrade-remote-loading="upgradeRemoteLoading"
            @search="handleRemoteSearch"
            @reset="handleRemoteReset"
            @select-category="handleSelectRemoteCategory"
            @select-tag="handleSelectRemoteTag"
            @select-price="handleSelectRemotePrice"
            @view-remote-detail="handleViewRemoteDetail"
            @install-remote="handleInstallRemote"
            @upgrade-remote="handleUpgradeRemote"
            @remote-page-change="handleRemotePageChange"
            @remote-page-size-change="handleRemotePageSizeChange"
          />
        </n-tab-pane>
      </n-tabs>
    </div>

    <!-- 详情抽屉 -->
    <n-drawer
      v-model:show="showDetailDrawer"
      :width="720"
      placement="right"
    >
      <n-drawer-content title="应用详情" closable>
        <n-tabs v-if="currentApplication" v-model:value="detailActiveTab" type="line">
          <!-- 基本信息标签页 -->
          <n-tab-pane name="basic" tab="基本信息">
            <div class="app-detail">
              <!-- 基本信息 -->
              <n-card title="基本信息" :bordered="false" class="detail-section">
                <n-descriptions :column="2" label-placement="left">
                  <n-descriptions-item label="应用ID">
                    {{ currentApplication.id }}
                  </n-descriptions-item>
                  <n-descriptions-item label="应用代码">
                    {{ currentApplication.applicationCode }}
                  </n-descriptions-item>
                  <n-descriptions-item label="应用名称">
                    {{ currentApplication.applicationName }}
                  </n-descriptions-item>
                  <n-descriptions-item label="插件ID">
                    {{ currentApplication.pluginId }}
                  </n-descriptions-item>
                  <n-descriptions-item label="插件版本">
                    {{ currentApplication.pluginVersion || '-' }}
                  </n-descriptions-item>
                  <n-descriptions-item label="应用类型">
                    <n-tag :type="getApplicationTypeColor(currentApplication.applicationType)" size="small">
                      {{ currentApplication.applicationTypeText }}
                    </n-tag>
                  </n-descriptions-item>
                  <n-descriptions-item v-if="currentApplication.pluginType" label="插件类型" :span="2">
                    <n-space :size="8">
                      <n-tag 
                        v-for="type in getPluginTypes(currentApplication.pluginType)" 
                        :key="type" 
                        size="small" 
                        :type="getPluginTypeColor(type)"
                      >
                        {{ getPluginTypeText(type) }}
                      </n-tag>
                    </n-space>
                  </n-descriptions-item>
                  <n-descriptions-item label="运行状态">
                    <n-tag :type="currentApplication.status === 1 ? 'success' : 'error'" size="small">
                      {{ currentApplication.status === 1 ? '运行中' : '已停止' }}
                    </n-tag>
                  </n-descriptions-item>
                  <n-descriptions-item label="默认应用">
                    <n-tag v-if="currentApplication.isDefault === 1" type="warning" size="small">是</n-tag>
                    <span v-else>否</span>
                  </n-descriptions-item>
                  <n-descriptions-item v-if="currentApplication.namespaceCode" label="命名空间">
                    {{ currentApplication.namespaceCode }}
                  </n-descriptions-item>
                </n-descriptions>

                <template v-if="currentApplication.description">
                  <n-divider />
                  <n-descriptions :column="1" label-placement="left">
                    <n-descriptions-item label="描述">
                      {{ currentApplication.description }}
                    </n-descriptions-item>
                  </n-descriptions>
                </template>
              </n-card>

              <!-- 作者信息 -->
              <n-card v-if="currentApplication.author || currentApplication.homepage" 
                      title="作者信息" 
                      :bordered="false" 
                      class="detail-section">
                <n-descriptions :column="2" label-placement="left">
                  <n-descriptions-item v-if="currentApplication.author" label="作者">
                    {{ currentApplication.author }}
                  </n-descriptions-item>
                  <n-descriptions-item v-if="currentApplication.homepage" label="主页" :span="2">
                    <a :href="currentApplication.homepage" target="_blank" rel="noopener noreferrer">
                      {{ currentApplication.homepage }}
                    </a>
                  </n-descriptions-item>
                </n-descriptions>
              </n-card>

              <!-- 时间信息 -->
              <n-card title="时间信息" :bordered="false" class="detail-section">
                <n-descriptions :column="2" label-placement="left">
                  <n-descriptions-item label="安装时间">
                    {{ formatDateTime(currentApplication.installTime) }}
                  </n-descriptions-item>
                  <n-descriptions-item label="更新时间">
                    {{ formatDateTime(currentApplication.updateTime) }}
                  </n-descriptions-item>
                  <n-descriptions-item v-if="currentApplication.createBy" label="创建人">
                    {{ currentApplication.createBy }}
                  </n-descriptions-item>
                  <n-descriptions-item v-if="currentApplication.updateBy" label="更新人">
                    {{ currentApplication.updateBy }}
                  </n-descriptions-item>
                </n-descriptions>
              </n-card>
            </div>
          </n-tab-pane>

          <!-- 操作日志标签页 -->
          <n-tab-pane name="logs" tab="操作日志">
            <div v-if="operationLogsLoading" class="loading-state">
              <n-spin size="large" />
            </div>

            <div v-else-if="operationLogs.length === 0" class="empty-state">
              <div class="empty-state__text">暂无操作日志</div>
            </div>

            <div v-else class="operation-log-list">
              <n-timeline>
                <n-timeline-item
                  v-for="log in operationLogs"
                  :key="log.id"
                  :type="log.status === 'SUCCESS' ? 'success' : 'error'"
                  :time="formatDateTime(log.createTime)"
                >
                  <template #header>
                    <div class="log-header">
                      <n-tag size="small" :type="getOperationTypeColor(log.operationType)">
                        {{ log.operationTypeText }}
                      </n-tag>
                      <n-tag size="small" :type="log.status === 'SUCCESS' ? 'success' : 'error'" style="margin-left: 8px">
                        {{ log.statusText }}
                      </n-tag>
                      <span style="margin-left: 8px; color: #666;">
                        {{ log.operationDesc }}
                      </span>
                    </div>
                  </template>

                  <div class="log-content">
                    <div class="log-row" v-if="log.operatorName">
                      <span class="log-label">操作人：</span>
                      <span class="log-value">{{ log.operatorName }}</span>
                    </div>

                    <div class="log-row" v-if="log.duration">
                      <span class="log-label">耗时：</span>
                      <span class="log-value">{{ log.duration }}ms</span>
                    </div>

                    <div class="log-row" v-if="log.message">
                      <span class="log-label">说明：</span>
                      <span class="log-value">{{ log.message }}</span>
                    </div>

                    <!-- 配置更新时显示配置差异 -->
                    <div v-if="log.operationType === 'CONFIG_UPDATE' && (log.beforeData || log.afterData)" class="config-diff">
                      <n-collapse>
                        <n-collapse-item title="查看配置变更" name="config">
                          <div class="config-compare">
                            <div class="config-column">
                              <div class="config-title">原配置</div>
                              <pre class="config-content">{{ formatJson(log.beforeData) }}</pre>
                            </div>
                            <div class="config-column">
                              <div class="config-title">新配置</div>
                              <pre class="config-content">{{ formatJson(log.afterData) }}</pre>
                            </div>
                          </div>
                        </n-collapse-item>
                      </n-collapse>
                    </div>
                  </div>
                </n-timeline-item>
              </n-timeline>

              <!-- 分页 -->
              <div v-if="operationLogsPagination.itemCount > 0" class="pagination" style="margin-top: 16px;">
                <n-pagination
                  v-model:page="operationLogsPagination.page"
                  v-model:page-size="operationLogsPagination.pageSize"
                  :page-count="Math.ceil(operationLogsPagination.itemCount / operationLogsPagination.pageSize)"
                  :page-sizes="[10, 20, 50]"
                  show-size-picker
                  @update:page="handleOperationLogsPageChange"
                  @update:page-size="handleOperationLogsPageSizeChange"
                />
              </div>
            </div>
          </n-tab-pane>
        </n-tabs>

        <template #footer>
          <n-space justify="end">
            <n-button @click="showDetailDrawer = false">关闭</n-button>
          </n-space>
        </template>
      </n-drawer-content>
    </n-drawer>

    <!-- 启动应用确认对话框 -->
    <n-modal
      v-model:show="showStartConfirm"
      preset="dialog"
      title="启动应用"
      positive-text="确定"
      negative-text="取消"
      :positive-button-props="{ loading: startLoading }"
      :loading="startLoading"
      @positive-click="confirmStart"
    >
      <n-alert type="info" :show-icon="true">
        确定要启动应用"{{ confirmTarget?.applicationName }}"吗？
      </n-alert>
    </n-modal>

    <!-- 停止应用确认对话框 -->
    <n-modal
      v-model:show="showStopConfirm"
      preset="dialog"
      title="停止应用"
      positive-text="确定"
      negative-text="取消"
      :positive-button-props="{ type: 'warning', loading: stopLoading }"
      :loading="stopLoading"
      @positive-click="confirmStop"
    >
      <n-alert type="warning" :show-icon="true">
        确定要停止应用"{{ confirmTarget?.applicationName }}"吗？停止后该应用将无法使用。
      </n-alert>
    </n-modal>

    <!-- 升级对话框 -->
    <n-modal
      v-model:show="showUpgradeModal"
      preset="dialog"
      title="升级应用"
      positive-text="确认升级"
      negative-text="取消"
      :positive-button-props="{ loading: upgradeLoading }"
      :loading="upgradeLoading"
      @positive-click="confirmUpgrade"
    >
      <n-form ref="upgradeFormRef" :model="upgradeForm" :rules="upgradeRules">
        <n-form-item label="目标版本" path="targetVersion">
          <n-input
            v-model:value="upgradeForm.targetVersion"
            placeholder="请输入目标版本号，如：1.0.1"
            :disabled="upgradeLoading"
          />
        </n-form-item>
        <n-alert type="info" :show-icon="false" style="margin-top: 16px">
          升级后将使用新版本的插件包
        </n-alert>
      </n-form>
    </n-modal>

    <!-- 卸载对话框 -->
    <n-modal
      v-model:show="showUninstallModal"
      preset="dialog"
      title="卸载应用"
      positive-text="确认卸载"
      negative-text="取消"
      :positive-button-props="{ type: 'error', loading: uninstallLoading }"
      :loading="uninstallLoading"
      @positive-click="confirmUninstall"
    >
      <n-form ref="uninstallFormRef" :model="uninstallForm" :rules="uninstallRules">
        <n-form-item label="卸载原因" path="reason">
          <n-input
            v-model:value="uninstallForm.reason"
            type="textarea"
            placeholder="请输入卸载原因（必填）"
            :rows="4"
            :disabled="uninstallLoading"
          />
        </n-form-item>
        <n-alert type="warning" :show-icon="false" style="margin-top: 16px">
          卸载后应用数据将被删除，此操作不可恢复！
        </n-alert>
      </n-form>
    </n-modal>

    <!-- 升级信息对话框 -->
    <n-modal
      v-model:show="showUpgradeInfoModal"
      preset="dialog"
      title="发现新版本"
      positive-text="立即升级"
      negative-text="稍后再说"
      :positive-button-props="{ loading: upgradeLoading }"
      @positive-click="handleUpgradeFromInfo"
    >
      <div v-if="upgradeInfoApp" class="upgrade-info">
        <n-alert type="info" :show-icon="false" style="margin-bottom: 16px">
          <template #header>
            <div style="display: flex; align-items: center; gap: 8px;">
              <n-icon size="20" color="#f59e0b">
                <component :is="RocketOutline" />
              </n-icon>
              <span style="font-weight: 600;">{{ upgradeInfoApp.applicationName }} 有新版本可用</span>
            </div>
          </template>
        </n-alert>

        <n-descriptions :column="1" label-placement="left" bordered>
          <n-descriptions-item label="应用名称">
            {{ upgradeInfoApp.applicationName }}
          </n-descriptions-item>
          <n-descriptions-item label="当前版本">
            <n-tag type="default" size="small">{{ upgradeInfoApp.pluginVersion }}</n-tag>
          </n-descriptions-item>
          <n-descriptions-item label="最新版本">
            <n-tag type="success" size="small">{{ upgradeInfoApp.remoteVersion }}</n-tag>
          </n-descriptions-item>
          <n-descriptions-item label="插件ID">
            {{ upgradeInfoApp.pluginId }}
          </n-descriptions-item>
        </n-descriptions>

        <n-alert type="warning" :show-icon="false" style="margin-top: 16px">
          升级前请确保已备份重要数据，升级过程中应用将暂时不可用
        </n-alert>
      </div>
    </n-modal>

    <!-- 升级日志对话框 -->
    <n-modal
      v-model:show="showUpgradeLogModal"
      preset="dialog"
      title="升级日志"
      :style="{ width: '720px' }"
      negative-text="关闭"
    >
      <div v-if="upgradeLogsLoading" class="loading-state">
        <n-spin size="large" />
      </div>
      <div v-else>
        <div v-if="upgradeLogs.length === 0" class="empty-state">
          <div class="empty-state__text">暂未查询到升级日志</div>
        </div>
        <div v-else class="upgrade-log-list">
          <n-timeline>
            <n-timeline-item
              v-for="log in upgradeLogs"
              :key="log.id"
              :type="log.status === 'SUCCESS' ? 'success' : 'error'"
              :time="formatDateTime(log.createTime)"
            >
              <template #header>
                <span>
                  版本 {{ log.oldVersion || '-' }} → {{ log.newVersion || '-' }}
                  <n-tag size="small" :type="log.status === 'SUCCESS' ? 'success' : 'error'" style="margin-left: 8px">
                    {{ log.status === 'SUCCESS' ? '成功' : '失败' }}
                  </n-tag>
                </span>
              </template>
              <div class="upgrade-log-item">
                <div class="upgrade-log-row">
                  <span class="meta-label">目标版本：</span>
                  <span class="meta-value">{{ log.targetVersion || '-' }}</span>
                </div>
                <div class="upgrade-log-row" v-if="log.pluginType">
                  <span class="meta-label">插件类型：</span>
                  <span class="meta-value">{{ log.pluginType }}</span>
                </div>
                <div class="upgrade-log-row" v-if="log.operatorName">
                  <span class="meta-label">操作人：</span>
                  <span class="meta-value">{{ log.operatorName }}</span>
                </div>
                <div class="upgrade-log-row" v-if="log.message">
                  <span class="meta-label">说明：</span>
                  <span class="meta-value">{{ log.message }}</span>
                </div>
              </div>
            </n-timeline-item>
          </n-timeline>
        </div>
      </div>
    </n-modal>

    <!-- 降级对话框 -->
    <n-modal
      v-model:show="showRollbackModal"
      preset="dialog"
      title="降级应用"
      positive-text="确认降级"
      negative-text="取消"
      :positive-button-props="{ type: 'warning', loading: rollbackLoading }"
      :loading="rollbackLoading"
      @positive-click="confirmRollback"
    >
      <n-form ref="rollbackFormRef" :model="rollbackForm">
        <n-form-item label="选择版本" path="targetVersion">
          <n-select
            v-model:value="rollbackForm.targetVersion"
            :options="rollbackVersionOptions"
            placeholder="请选择要降级到的版本"
            :loading="rollbackVersionsLoading"
            :disabled="rollbackLoading"
            @update:value="handleRollbackVersionChange"
          />
        </n-form-item>
        
        <n-form-item v-if="rollbackForm.targetVersion && selectedRollbackLog" label="版本信息">
          <n-card size="small" :bordered="true">
            <n-descriptions :column="1" label-placement="left" size="small">
              <n-descriptions-item label="版本号">
                {{ selectedRollbackLog.newVersion || '-' }}
              </n-descriptions-item>
              <n-descriptions-item label="升级时间">
                {{ formatDateTime(selectedRollbackLog.createTime) }}
              </n-descriptions-item>
              <n-descriptions-item v-if="selectedRollbackLog.operatorName" label="操作人">
                {{ selectedRollbackLog.operatorName }}
              </n-descriptions-item>
              <n-descriptions-item v-if="selectedRollbackLog.message" label="升级说明">
                {{ selectedRollbackLog.message }}
              </n-descriptions-item>
            </n-descriptions>
          </n-card>
        </n-form-item>

        <n-alert type="warning" :show-icon="false" style="margin-top: 16px">
          <template #header>
            <div style="font-weight: 600;">注意事项</div>
          </template>
          <ul style="margin: 8px 0 0 0; padding-left: 20px;">
            <li>降级操作会将应用回滚到指定版本</li>
            <li>降级过程中应用将暂时不可用</li>
            <li>请确保已备份重要数据</li>
          </ul>
        </n-alert>
      </n-form>
    </n-modal>

    <!-- 应用配置对话框 -->
    <n-modal
      v-model:show="showConfigModal"
      preset="dialog"
      title="应用配置"
      positive-text="保存"
      negative-text="取消"
      :positive-button-props="{ loading: configLoading }"
      :loading="configLoading"
      :style="{ width: '900px' }"
      @positive-click="confirmConfig"
    >
      <n-scrollbar style="max-height: 70vh;">
        <n-spin :show="configMetadataLoading">
          <div class="config-modal-content">
            <!-- 应用信息卡片 -->
            <n-card size="small" :bordered="false" class="app-info-card">
              <div class="app-info-row">
                <div class="app-info-item">
                  <span class="info-label">应用名称：</span>
                  <span class="info-value">{{ configTargetApp?.applicationName }}</span>
                </div>
                <div class="app-info-item">
                  <span class="info-label">应用代码：</span>
                  <n-text type="info" class="info-value">{{ configTargetApp?.applicationCode }}</n-text>
                </div>
              </div>
            </n-card>

            <!-- 动态扩展配置 -->
            <template v-if="configMetadata.length > 0">
              <n-card size="small" title="扩展配置" :bordered="false" class="config-section">
                <DynamicFormRenderer
                  v-model="configForm.extensionConfig"
                  :metadata="{ fields: configMetadata }"
                />
              </n-card>
            </template>

            <!-- 高级配置：默认展开，内含「表面/预加载」可视化卡片 -->
            <n-collapse
              v-model:expanded-names="configCollapseExpanded"
              class="config-section config-advanced-collapse"
            >
              <n-collapse-item title="高级配置" name="advanced">
                <n-form ref="configFormRef" :model="configForm" label-placement="left" label-width="120" size="small">
                  <n-card size="small" :bordered="true" class="config-surface-card">
                    <template #header>
                      <div class="config-surface-card-header">
                        <span class="config-surface-card-title">前端表面与预加载</span>
                        <n-tooltip trigger="hover">
                          <template #trigger>
                            <n-text depth="3" style="cursor: help; font-size: 12px;">说明</n-text>
                          </template>
                          <div style="max-width: 320px; line-height: 1.5;">
                            <div><strong>B 端表面</strong>：是否出现在管理端应用列表（<code>GET /applications</code> 已登录）。</div>
                            <div style="margin-top: 6px;"><strong>C 端表面</strong>：是否具备公网/访客侧前端能力。</div>
                            <div style="margin-top: 6px;"><strong>预加载(B)</strong>：<code>/applications/bootstrap/admin</code> 是否返回该应用。</div>
                            <div style="margin-top: 6px;"><strong>预加载(C)</strong>：<code>/applications/bootstrap/consumer</code> 是否返回该应用。</div>
                          </div>
                        </n-tooltip>
                      </div>
                    </template>
                    <div class="surface-preload-grid">
                      <div class="surface-preload-item">
                        <div class="surface-preload-label">B 端表面（管理端列表）</div>
                        <n-switch v-model:value="configForm.surfaceAdmin" :disabled="configLoading" size="medium">
                          <template #checked>开启</template>
                          <template #unchecked>关闭</template>
                        </n-switch>
                        <div class="surface-preload-desc">在应用切换器等管理界面中展示并可授权</div>
                      </div>
                      <div class="surface-preload-item">
                        <div class="surface-preload-label">C 端表面（公网 / 访客）</div>
                        <n-switch v-model:value="configForm.surfaceConsumer" :disabled="configLoading" size="medium">
                          <template #checked>开启</template>
                          <template #unchecked>关闭</template>
                        </n-switch>
                        <div class="surface-preload-desc">独立门户、匿名路由等 C 端前端能力</div>
                      </div>
                      <div class="surface-preload-item">
                        <div class="surface-preload-label">管理壳预加载</div>
                        <n-switch v-model:value="configForm.autoLoadAdmin" :disabled="configLoading" size="medium">
                          <template #checked>开启</template>
                          <template #unchecked>关闭</template>
                        </n-switch>
                        <div class="surface-preload-desc">登录后宿主初始化时自动拉取该应用前端包</div>
                      </div>
                      <div class="surface-preload-item">
                        <div class="surface-preload-label">C 端预加载</div>
                        <n-switch v-model:value="configForm.autoLoadConsumer" :disabled="configLoading" size="medium">
                          <template #checked>开启</template>
                          <template #unchecked>关闭</template>
                        </n-switch>
                        <div class="surface-preload-desc">未登录或访客场景下 bootstrap 时预加载该应用</div>
                      </div>
                    </div>
                    <template #footer>
                      <n-text depth="3" style="font-size: 12px;">
                        修改后需保存；与 <code>plugin.yml</code> 中 UI/扩展字段安装时写入的值可在此覆盖。
                      </n-text>
                    </template>
                  </n-card>

                  <div class="advanced-config-grid advanced-config-grid--startup">
                    <n-form-item label="启动时加载" path="loadOnStartup">
                      <n-switch 
                        v-model:value="configForm.loadOnStartup" 
                        :disabled="configLoading || !configForm.autoLoadAdmin"
                        size="small"
                      >
                        <template #checked>开启</template>
                        <template #unchecked>关闭</template>
                      </n-switch>
                    </n-form-item>

                    <n-form-item label="启动优先级" path="startPriority">
                      <n-input-number
                        v-model:value="configForm.startPriority"
                        :min="0"
                        :max="100"
                        :disabled="configLoading || !configForm.autoLoadAdmin"
                        size="small"
                        style="width: 120px;"
                      />
                    </n-form-item>

                    <n-form-item label="启动延迟(ms)" path="startDelay">
                      <n-input-number
                        v-model:value="configForm.startDelay"
                        :min="0"
                        :max="60000"
                        :step="1000"
                        :disabled="configLoading || !configForm.autoLoadAdmin"
                        size="small"
                        style="width: 120px;"
                      />
                    </n-form-item>
                  </div>

                  <n-form-item label="配置描述" path="description">
                    <n-input
                      v-model:value="configForm.description"
                      type="textarea"
                      placeholder="可选，描述此配置的用途"
                      :rows="2"
                      :disabled="configLoading"
                      size="small"
                    />
                  </n-form-item>

                  <n-alert type="info" :show-icon="false" size="small" style="margin-top: 12px;">
                    <ul style="margin: 0; padding-left: 20px; font-size: 12px; line-height: 1.6;">
                      <li>「前端表面与预加载」四项写入扩展配置中的 <code>surfaceAdmin</code>、<code>surfaceConsumer</code>、<code>autoLoadAdmin</code>、<code>autoLoadConsumer</code></li>
                      <li>启动时加载 / 优先级 / 延迟：依赖「管理壳预加载」开启后通常才有意义</li>
                    </ul>
                  </n-alert>
                </n-form>
              </n-collapse-item>
            </n-collapse>
          </div>
        </n-spin>
      </n-scrollbar>
    </n-modal>

    <!-- 安装前配置对话框 -->
    <n-modal
      v-model:show="showInstallConfigModal"
      preset="dialog"
      title="安装前配置"
      positive-text="开始安装"
      negative-text="取消"
      :positive-button-props="{ loading: installConfigSubmitting }"
      :loading="installConfigSubmitting"
      :style="{ width: '820px' }"
      @positive-click="confirmInstallWithConfig"
    >
      <n-scrollbar style="max-height: 70vh;">
        <n-spin :show="installConfigModalLoading">
          <div class="config-modal-content">
            <n-card size="small" :bordered="false" class="app-info-card">
              <div class="app-info-row">
                <div class="app-info-item">
                  <span class="info-label">安装对象：</span>
                  <span class="info-value">{{ installConfigTargetLabel }}</span>
                </div>
                <div class="app-info-item">
                  <span class="info-label">安装方式：</span>
                  <n-text type="info" class="info-value">
                    {{ installConfigContext?.mode === 'upload' ? '上传安装' : '远程安装' }}
                  </n-text>
                </div>
              </div>
            </n-card>

            <n-card
              v-if="installConfigMetadata.length > 0"
              size="small"
              title="安装参数"
              :bordered="false"
              class="config-section"
            >
              <DynamicFormRenderer
                ref="installConfigFormRendererRef"
                v-model="installConfigForm"
                :metadata="{ fields: installConfigMetadata }"
              />
            </n-card>

            <n-alert type="info" :show-icon="false" style="margin-top: 16px;">
              <template #header>
                <div style="font-weight: 600;">说明</div>
              </template>
              <div style="margin-top: 8px; line-height: 1.7;">
                这些参数会在插件安装前写入安装流程，并在安装成功后同步持久化到应用扩展配置中。
              </div>
            </n-alert>
          </div>
        </n-spin>
      </n-scrollbar>
    </n-modal>

    <!-- 上传应用包对话框 -->
    <n-modal
      v-model:show="showUploadModal"
      preset="dialog"
      title="上传应用包"
      positive-text="确认安装"
      negative-text="取消"
      :positive-button-props="{ disabled: !uploadFile, loading: uploading }"
      :loading="uploading"
      @positive-click="confirmUpload"
    >
      <n-form ref="uploadFormRef" :model="uploadForm">
        <n-form-item label="应用包文件">
          <n-upload
            ref="uploadRef"
            :max="1"
            :default-upload="false"
            accept=".jar"
            :disabled="uploading"
            @change="handleFileChange"
          >
            <n-upload-dragger>
              <div style="margin-bottom: 12px">
                <n-icon size="48" :depth="3">
                  <component :is="CloudUploadOutline" />
                </n-icon>
              </div>
              <n-text style="font-size: 16px">
                点击或拖拽文件到此区域上传
              </n-text>
              <n-p depth="3" style="margin: 8px 0 0 0">
                仅支持 .jar 格式的应用包文件
              </n-p>
            </n-upload-dragger>
          </n-upload>
        </n-form-item>

        <n-alert v-if="uploadFile" type="info" :show-icon="false" style="margin-top: 16px">
          <template #header>
            <div style="font-weight: 600;">已选择文件</div>
          </template>
          <div style="margin-top: 8px;">
            <div><strong>文件名：</strong>{{ uploadFile.name }}</div>
            <div><strong>大小：</strong>{{ formatFileSize(uploadFile.file?.size || 0) }}</div>
          </div>
        </n-alert>

        <n-alert type="warning" :show-icon="false" style="margin-top: 16px">
          <template #header>
            <div style="font-weight: 600;">注意事项</div>
          </template>
          <ul style="margin: 8px 0 0 0; padding-left: 20px;">
            <li>请确保上传的是有效的应用包文件</li>
            <li>安装过程中请勿关闭页面</li>
            <li>安装完成后应用将自动启用</li>
          </ul>
        </n-alert>
      </n-form>
    </n-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, computed, getCurrentInstance } from 'vue'
import { useMessage } from '@keqi.gress/plugin-bridge'
import HeaderActions from './application-management/components/HeaderActions.vue'
import LocalAppsTab from './application-management/components/LocalAppsTab.vue'
import RemoteStoreTab from './application-management/components/RemoteStoreTab.vue'
import RemoteDetailInline from './application-management/components/RemoteDetailInline.vue'
import type { FilterFieldConfig } from './application-management/types'
import {
  NSpace,
  NButton,
  NTag,
  NIcon,
  NDrawer,
  NDrawerContent,
  NDescriptions,
  NDescriptionsItem,
  NDivider,
  NCard,
  NAlert,
  NModal,
  NForm,
  NFormItem,
  NInput,
  NSpin,
  NPagination,
  NTabs,
  NTabPane,
  NTooltip,
  NUpload,
  NUploadDragger,
  NText,
  NP,
  NTimeline,
  NTimelineItem,
  NSelect,
  NSwitch,
  NInputNumber,
  NCollapse,
  NCollapseItem,
  NScrollbar,
  type FormInst,
  type FormRules,
  type UploadFileInfo,
  type UploadInst,
  NDropdown
} from 'naive-ui'
import { useDialog } from 'naive-ui'
import { useIcon } from '@keqi.gress/plugin-bridge'
import { applicationApi } from '../api/application'

import type { Application, ApplicationType, ApplicationUpgradeLog } from '../types/application'

// 图标
const Refresh = useIcon('RefreshOutline')
const Eye = useIcon('EyeOutline')
const PowerOutline = useIcon('PowerOutline')
const PowerOffOutline = useIcon('PowerOffOutline')
const CloudUploadOutline = useIcon('CloudUploadOutline')
const TrashOutline = useIcon('TrashOutline')
const AppsOutline = useIcon('AppsOutline')
const CubeOutline = useIcon('CubeOutline')
const ExtensionPuzzleOutline = useIcon('ExtensionPuzzleOutline')
const TimeOutline = useIcon('TimeOutline')
const RocketOutline = useIcon('RocketOutline')
const EllipsisHorizontalOutline = useIcon('EllipsisHorizontalOutline')
const LayersOutline = useIcon('LayersOutline')
const ChevronBackOutline = useIcon('ChevronBackOutline')

const categories = ref<Array<{ categoryKey: string; categoryName: string; description?: string }>>([])
const bizTags = ref<Array<{ categoryKey: string; categoryName: string; description?: string }>>([])
const typeOptions = computed(() =>
  (categories.value || [])
    .filter((c) => c && c.categoryKey && c.categoryName)
    .map((c) => ({
      id: c.categoryKey,
      label: c.categoryName,
      desc: c.description || ''
    }))
)
const tagOptions = computed(() =>
  (bizTags.value || [])
    .filter((c) => c && c.categoryKey && c.categoryName)
    .map((c) => ({
      id: c.categoryKey,
      label: c.categoryName,
      desc: c.description || ''
    }))
)

// FilterFieldConfig 类型定义
export type { FilterFieldConfig, FilterFieldType } from './application-management/types'

// Message & Dialog
const message = useMessage()
const dialog = useDialog()

// 确认对话框状态
const showStartConfirm = ref(false)
const showStopConfirm = ref(false)
const confirmTarget = ref<Application | null>(null)

// Loading states for各个操作
const refreshLoading = ref(false)
const startLoading = ref(false)
const stopLoading = ref(false)
const upgradeLoading = ref(false)
const uninstallLoading = ref(false)
const restartLoading = ref(false)
const installRemoteLoading = ref<Record<string, boolean>>({})
const upgradeRemoteLoading = ref<Record<string, boolean>>({})

// State
const activeTab = ref('local')
const loading = ref(false)
const tableData = ref<Application[]>([])
const showDetailDrawer = ref(false)
const currentApplication = ref<Application | null>(null)

// 详情标签页
const detailActiveTab = ref('basic')

// 操作日志相关
const operationLogs = ref<any[]>([])
const operationLogsLoading = ref(false)
const operationLogsPagination = reactive({
  page: 1,
  pageSize: 20,
  itemCount: 0
})

// Remote state
const remoteLoading = ref(false)
const remoteTableData = ref<Application[]>([])

// 远程详情（页面内展示，不跳转路由）
const remoteDetailVisible = ref(false)
const remoteDetailTab = ref<'intro' | 'version'>('intro')
const remoteDetailLoading = ref(false)
const remoteDetail = ref<Application | null>(null)
const remoteDetailInstalling = computed(() => Boolean(remoteDetail.value?.pluginId && installRemoteLoading.value[remoteDetail.value.pluginId]))
const remoteDetailUpgrading = computed(() => Boolean(remoteDetail.value?.pluginId && upgradeRemoteLoading.value[remoteDetail.value.pluginId]))
const remoteDetailVersionsLoading = ref(false)
const remoteDetailVersions = ref<
  Array<{
    pluginId: string
    version: string
    releaseNotes?: string
    fileSize?: number
    uploadTime?: string
    current?: boolean
  }>
>([])
const remoteDetailSelectedVersion = ref('')

// 过滤器状态
const showAdvanced = ref(false)
const filters = ref({
  keyword: '',
  status: null as number | null,
  applicationType: null as ApplicationType | null,
  clientType: null as 'B' | 'C' | null,
  preloadEnabled: null as 0 | 1 | null,
  typeKey: '',
  tag: ''
})

// 远程应用过滤器状态
const showRemoteAdvanced = ref(false)
const remoteFilters = ref({
  keyword: '',
  category: '',
  tag: '',
  priceType: ''
})

// Pagination
const pagination = reactive({
  page: 1,
  pageSize: 20,
  itemCount: 0,
  showSizePicker: true,
  pageSizes: [10, 20, 50, 100]
})

// Remote pagination
const remotePagination = reactive({
  page: 1,
  pageSize: 20,
  itemCount: 0,
  showSizePicker: true,
  pageSizes: [10, 20, 50, 100]
})

// 升级表单
const showUpgradeModal = ref(false)
const upgradeFormRef = ref<FormInst | null>(null)
const upgradeForm = reactive({
  targetVersion: ''
})
const upgradeRules: FormRules = {
  targetVersion: [
    { required: true, message: '请输入目标版本号', trigger: 'blur' }
  ]
}

// 卸载表单
const showUninstallModal = ref(false)
const uninstallFormRef = ref<FormInst | null>(null)
const uninstallForm = reactive({
  reason: ''
})
const uninstallRules: FormRules = {
  reason: [
    { required: true, message: '请输入卸载原因', trigger: 'blur' }
  ]
}

// 升级信息弹窗
const showUpgradeInfoModal = ref(false)
const upgradeInfoApp = ref<Application | null>(null)

// 上传应用包
const showUploadModal = ref(false)
const uploadFormRef = ref<FormInst | null>(null)
const uploadRef = ref<UploadInst | null>(null)
const uploadForm = reactive({})
const uploadFile = ref<UploadFileInfo | null>(null)
const uploading = ref(false)
const showInstallConfigModal = ref(false)
const installConfigModalLoading = ref(false)
const installConfigSubmitting = ref(false)
const installConfigMetadata = ref<any[]>([])
const installConfigForm = ref<Record<string, any>>({})
const installConfigFormRendererRef = ref<{ validate: () => Promise<boolean> } | null>(null)
const installConfigContext = ref<{
  mode: 'remote' | 'upload'
  app?: Application | null
  file?: UploadFileInfo | null
} | null>(null)
const installConfigTargetLabel = computed(() => {
  if (installConfigContext.value?.mode === 'upload') {
    return installConfigContext.value.file?.name || '-'
  }
  return installConfigContext.value?.app?.applicationName || installConfigContext.value?.app?.pluginId || '-'
})

const AGGREGATE_PAGE = '/plugins/appstore/aggregate-applications'

function goToAggregateManagement() {
  const router = getCurrentInstance()?.appContext.config.globalProperties.$router as
    | { push?: (p: string) => void }
    | undefined
  if (router?.push) {
    router.push(AGGREGATE_PAGE)
  } else {
    window.location.assign(AGGREGATE_PAGE)
  }
}

/** 聚合应用在配置了 icon 时用图片展示（支持绝对 URL 与站点内相对路径） */
function useAggregateIconImg(app: Application): boolean {
  return Boolean(app.aggregateApp && app.icon?.trim())
}

function localAppIconModifierClass(app: Application): string {
  if (app.aggregateApp) return 'app-icon--aggregated'
  return `app-icon--${app.applicationType}`
}

function localAppIconComponent(app: Application) {
  if (app.aggregateApp) return LayersOutline
  if (app.applicationType === 'integrated') return CubeOutline
  return ExtensionPuzzleOutline
}

// 升级日志
const showUpgradeLogModal = ref(false)
const upgradeLogsLoading = ref(false)
const upgradeLogs = ref<ApplicationUpgradeLog[]>([])

// 降级相关
const showRollbackModal = ref(false)
const rollbackLoading = ref(false)
const rollbackVersionsLoading = ref(false)
const rollbackFormRef = ref<FormInst | null>(null)
const rollbackForm = reactive({
  targetVersion: ''
})
const rollbackVersionOptions = ref<Array<{ label: string; value: string }>>([])
const selectedRollbackLog = ref<ApplicationUpgradeLog | null>(null)
const rollbackTargetApp = ref<Application | null>(null)

// 应用配置相关
const showConfigModal = ref(false)
const configLoading = ref(false)
const configFormRef = ref<FormInst | null>(null)
const configTargetApp = ref<Application | null>(null)
const configForm = reactive({
  surfaceAdmin: false,
  surfaceConsumer: false,
  autoLoadAdmin: false,
  autoLoadConsumer: false,
  loadOnStartup: false,
  startPriority: 50,
  startDelay: 0,
  description: '',
  extensionConfig: {} as Record<string, any>
})
const configMetadata = ref<any[]>([])
const configMetadataLoading = ref(false)
/** 打开配置弹窗时始终展开「高级配置」，避免有扩展元数据时面板默认收起看不到开关 */
const configCollapseExpanded = ref<string[]>(['advanced'])

// 过滤字段配置
const basicFields: FilterFieldConfig[] = [
  {
    key: 'keyword',
    label: '关键词',
    type: 'input',
    placeholder: '搜索应用名称、代码、插件ID'
  },
  {
    key: 'status',
    label: '运行状态',
    type: 'select',
    placeholder: '请选择状态',
    options: [
      { label: '全部', value: null },
      { label: '运行中', value: 1 },
      { label: '已停止', value: 0 }
    ]
  },
  {
    key: 'applicationType',
    label: '应用类型',
    type: 'select',
    placeholder: '请选择类型',
    options: [
      { label: '全部', value: null },
      { label: '集成应用', value: 'integrated' },
      { label: '插件应用', value: 'plugin' },
      { label: '聚合应用', value: 'aggregated' }
    ]
  },
  {
    key: 'clientType',
    label: '客户端',
    type: 'select',
    placeholder: '请选择',
    options: [
      { label: '全部', value: null },
      { label: 'B端', value: 'B' },
      { label: 'C端', value: 'C' }
    ]
  },
  {
    key: 'preloadEnabled',
    label: '预加载',
    type: 'select',
    placeholder: '请选择',
    options: [
      { label: '全部', value: null },
      { label: '开启', value: 1 },
      { label: '关闭', value: 0 }
    ]
  }
]

// 远程应用过滤字段配置
const remoteBasicFields: FilterFieldConfig[] = [
  {
    key: 'keyword',
    label: '关键词',
    type: 'input',
    placeholder: '搜索应用名称、代码、插件ID'
  }
]

// Methods
const loadData = async () => {
  refreshLoading.value = true
  try {
    if (activeTab.value === 'local') {
      await loadLocalData()
    } else {
      await loadRemoteData()
    }
  } finally {
    refreshLoading.value = false
  }
}

const loadLocalData = async () => {
  loading.value = true
  try {
    const params: any = {
      page: pagination.page,
      size: pagination.pageSize
    }
    
    if (filters.value.keyword && filters.value.keyword.trim()) {
      params.keyword = filters.value.keyword.trim()
    }
    if (filters.value.status !== null) {
      params.status = filters.value.status
    }
    if (filters.value.applicationType) {
      params.applicationType = filters.value.applicationType
    }
    if (filters.value.clientType) {
      params.clientType = filters.value.clientType
    }
    if (filters.value.preloadEnabled !== null) {
      params.preloadEnabled = filters.value.preloadEnabled
    }
    if (filters.value.tag && String(filters.value.tag).trim()) {
      params.tag = String(filters.value.tag).trim()
    }

    const data = await applicationApi.getList(params)
    tableData.value = data.items
    pagination.itemCount = data.total
  } catch (error: any) {
    // 错误已由 request.ts 拦截器处理，这里只需捕获避免未处理的 Promise rejection
    console.error('加载应用列表失败:', error)
  } finally {
    loading.value = false
  }
}

const loadRemoteData = async () => {
  remoteLoading.value = true
  try {
    const params: any = {
      page: remotePagination.page,
      size: remotePagination.pageSize
    }
    
    if (remoteFilters.value.keyword && remoteFilters.value.keyword.trim()) {
      params.keyword = remoteFilters.value.keyword.trim()
    }

    if (remoteFilters.value.category && String(remoteFilters.value.category).trim()) {
      params.category = String(remoteFilters.value.category).trim()
    }
    if (remoteFilters.value.tag && String(remoteFilters.value.tag).trim()) {
      params.tag = String(remoteFilters.value.tag).trim()
    }
    if (remoteFilters.value.priceType && String(remoteFilters.value.priceType).trim()) {
      params.priceType = String(remoteFilters.value.priceType).trim()
    }

    const data = await applicationApi.getRemoteList(params)
    remoteTableData.value = data.items
    remotePagination.itemCount = data.total
  } catch (error: any) {
    // 错误已由 request.ts 拦截器处理
    console.error('加载远程应用列表失败:', error)
  } finally {
    remoteLoading.value = false
  }
}

const handleSearch = () => {
  pagination.page = 1
  loadLocalData()
}

const handleReset = () => {
  filters.value.keyword = ''
  filters.value.status = null
  filters.value.applicationType = null
  filters.value.clientType = null
  filters.value.preloadEnabled = null
  filters.value.typeKey = ''
  filters.value.tag = ''
  loadBizTags()
  pagination.page = 1
  loadLocalData()
}

function handleSelectType(typeKey: string) {
  filters.value.typeKey = typeKey || ''
  filters.value.tag = ''
  loadBizTags(filters.value.typeKey)
  pagination.page = 1
  loadLocalData()
}

function handleSelectTag(tagId: string) {
  filters.value.tag = tagId || ''
  pagination.page = 1
  loadLocalData()
}

const handleRemoteSearch = () => {
  remotePagination.page = 1
  loadRemoteData()
}

const handleRemoteReset = () => {
  remoteFilters.value.keyword = ''
  remoteFilters.value.category = ''
  remoteFilters.value.tag = ''
  remoteFilters.value.priceType = ''
  loadBizTags()
  remotePagination.page = 1
  loadRemoteData()
}

function handleSelectRemoteCategory(categoryKey: string) {
  remoteFilters.value.category = categoryKey || ''
  remoteFilters.value.tag = ''
  loadBizTags(remoteFilters.value.category)
  remotePagination.page = 1
  loadRemoteData()
}

function handleSelectRemoteTag(tagKey: string) {
  remoteFilters.value.tag = tagKey || ''
  remotePagination.page = 1
  loadRemoteData()
}

function handleSelectRemotePrice(priceType: string) {
  remoteFilters.value.priceType = priceType || ''
  remotePagination.page = 1
  loadRemoteData()
}

const handleTabChange = (value: string) => {
  activeTab.value = value
  loadData()
}

const handlePageChange = (page: number) => {
  pagination.page = page
  loadLocalData()
}

const handlePageSizeChange = (pageSize: number) => {
  pagination.pageSize = pageSize
  pagination.page = 1
  loadLocalData()
}

const handleRemotePageChange = (page: number) => {
  remotePagination.page = page
  loadRemoteData()
}

const handleRemotePageSizeChange = (pageSize: number) => {
  remotePagination.pageSize = pageSize
  remotePagination.page = 1
  loadRemoteData()
}

const handleViewDetail = async (app: Application) => {
  try {
    const data = await applicationApi.getDetail(app.id)
    currentApplication.value = data
    showDetailDrawer.value = true
    detailActiveTab.value = 'basic'
    
    // 加载操作日志
    loadOperationLogs(app.id)
  } catch (error: any) {
    // 错误已由 request.ts 拦截器处理
    console.error('加载应用详情失败:', error)
  }
}

// 加载操作日志
const loadOperationLogs = async (appId: number) => {
  operationLogsLoading.value = true
  try {
    const data = await applicationApi.getOperationLogs(
      appId,
      operationLogsPagination.page,
      operationLogsPagination.pageSize
    )
    operationLogs.value = data.items
    operationLogsPagination.itemCount = data.total
  } catch (error: any) {
  } finally {
    operationLogsLoading.value = false
  }
}

// 操作日志分页
const handleOperationLogsPageChange = (page: number) => {
  operationLogsPagination.page = page
  if (currentApplication.value) {
    loadOperationLogs(currentApplication.value.id)
  }
}

const handleOperationLogsPageSizeChange = (pageSize: number) => {
  operationLogsPagination.pageSize = pageSize
  operationLogsPagination.page = 1
  if (currentApplication.value) {
    loadOperationLogs(currentApplication.value.id)
  }
}

const handleViewRemoteDetail = async (app: Application) => {
  remoteDetailVisible.value = true
  remoteDetailTab.value = 'intro'
  remoteDetailLoading.value = true
  try {
    remoteDetail.value = await applicationApi.getRemoteDetail(app.pluginId)
    remoteDetailVersionsLoading.value = true
    remoteDetailVersions.value = await applicationApi.getRemoteVersions(app.pluginId)
    remoteDetailSelectedVersion.value =
      remoteDetailVersions.value.find((v) => v.current)?.version ||
      remoteDetailVersions.value[0]?.version ||
      remoteDetail.value?.pluginVersion ||
      ''
  } finally {
    remoteDetailLoading.value = false
    remoteDetailVersionsLoading.value = false
  }
}

function closeRemoteDetail() {
  remoteDetailVisible.value = false
  remoteDetail.value = null
  remoteDetailVersions.value = []
  remoteDetailSelectedVersion.value = ''
}

function handleSelectRemoteDetailVersion(version: string) {
  remoteDetailSelectedVersion.value = version
}

function downloadRemoteVersion(pluginId: string, version: string) {
  const url = `/plugins/appstore/applications/remote/${encodeURIComponent(pluginId)}/versions/${encodeURIComponent(version)}/download`
  window.open(url, '_blank')
}

const handleStart = (app: Application) => {
  confirmTarget.value = app
  showStartConfirm.value = true
}

const confirmStart = async () => {
  if (!confirmTarget.value) return false
  
  startLoading.value = true
  try {
    await applicationApi.enable(confirmTarget.value.id)
    message.success('应用已启动，页面即将刷新...')
    showStartConfirm.value = false
    
    // 延迟刷新页面，让用户看到成功提示
    setTimeout(() => {
      window.location.reload()
    }, 1500)
    
    return true
  } catch (error: any) {
    console.error('启动应用失败:', error)
    return false
  } finally {
    startLoading.value = false
  }
}

const handleStop = (app: Application) => {
  confirmTarget.value = app
  showStopConfirm.value = true
}

const confirmStop = async () => {
  if (!confirmTarget.value) return false
  
  stopLoading.value = true
  try {
    await applicationApi.disable(confirmTarget.value.id)
    message.success('应用已停止，页面即将刷新...')
    showStopConfirm.value = false
    
    // 延迟刷新页面，让用户看到成功提示
    setTimeout(() => {
      window.location.reload()
    }, 1500)
    
    return true
  } catch (error: any) {
    console.error('停止应用失败:', error)
    return false
  } finally {
    stopLoading.value = false
  }
}

const handleUpgrade = (app: Application) => {
  currentApplication.value = app
  upgradeForm.targetVersion = app.remoteVersion || ''
  showUpgradeModal.value = true
}

const confirmUpgrade = async () => {
  try {
    await upgradeFormRef.value?.validate()
  } catch {
    return false
  }
  
  if (!currentApplication.value) return false
  
  upgradeLoading.value = true
  try {
    await applicationApi.upgrade(currentApplication.value.id, {
      targetVersion: upgradeForm.targetVersion
    })
    
    message.success('应用升级成功，页面即将刷新...')
    showUpgradeModal.value = false
    
    // 延迟刷新页面，让用户看到成功提示
    setTimeout(() => {
      window.location.reload()
    }, 1500)
    
    return true
  } catch (error: any) {
    console.error('升级应用失败:', error)
    return false
  } finally {
    upgradeLoading.value = false
  }
}

const handleUninstall = (app: Application) => {
  currentApplication.value = app
  uninstallForm.reason = ''
  showUninstallModal.value = true
}

const confirmUninstall = async () => {
  try {
    await uninstallFormRef.value?.validate()
  } catch {
    return false
  }
  
  if (!currentApplication.value) return false
  
  uninstallLoading.value = true
  try {
    await applicationApi.uninstall(currentApplication.value.id, {
      reason: uninstallForm.reason
    })
    
    message.success('应用卸载成功，页面即将刷新...')
    showUninstallModal.value = false
    
    // 延迟刷新页面，让用户看到成功提示
    setTimeout(() => {
      window.location.reload()
    }, 1500)
    
    return true
  } catch (error: any) {
    console.error('卸载应用失败:', error)
    return false
  } finally {
    uninstallLoading.value = false
  }
}

const executeRemoteInstall = async (app: Application, installConfig: Record<string, any> = {}) => {
  installRemoteLoading.value[app.pluginId] = true
  message.info(`正在安装应用: ${app.applicationName}...`)
  try {
    await applicationApi.installRemote({
      pluginId: app.pluginId,
      installConfig
    })
    if (app.pluginId === 'iam') {
      message.success('IAM 安装成功，首次初始化已触发。请留意服务日志中的“IAM 首次初始化完成”摘要，页面即将刷新...')
    } else {
      message.success('应用安装成功，页面即将刷新...')
    }
    setTimeout(() => window.location.reload(), 1500)
  } catch (error: any) {
    console.error('远程安装应用失败:', error)
    message.error(error?.message || '应用安装失败')
    throw error
  } finally {
    installRemoteLoading.value[app.pluginId] = false
  }
}

const openInstallConfigModal = (
  context: { mode: 'remote' | 'upload'; app?: Application | null; file?: UploadFileInfo | null },
  metadata: any[]
) => {
  installConfigContext.value = context
  installConfigMetadata.value = metadata || []
  installConfigForm.value = {}
  showInstallConfigModal.value = true
}

const handleInstallRemote = async (app: Application) => {
  if (!app.pluginId) {
    message.error('插件ID不能为空')
    return
  }

  installConfigModalLoading.value = true
  try {
    const metadata = await applicationApi.getRemoteInstallConfigMetadata(app.pluginId)
    if (metadata && metadata.length > 0) {
      openInstallConfigModal({ mode: 'remote', app }, metadata)
      return
    }
  } catch (error: any) {
    console.error('获取远程安装前配置失败:', error)
    message.error(error?.message || '获取安装前配置失败')
    return
  } finally {
    installConfigModalLoading.value = false
  }
  
  const dialogReactive = dialog.warning({
    title: '安装应用',
    content: `确定要安装应用 "${app.applicationName}" 吗？`,
    positiveText: '安装',
    negativeText: '取消',
    positiveButtonProps: {
      loading: false
    },
    negativeButtonProps: {
      disabled: false
    },
    onPositiveClick: async () => {
      dialogReactive.positiveButtonProps = {
        ...dialogReactive.positiveButtonProps,
        loading: true
      }
      dialogReactive.negativeButtonProps = {
        ...dialogReactive.negativeButtonProps,
        disabled: true
      }
      try {
        await executeRemoteInstall(app)
      } catch (error: any) {
      } finally {
        dialogReactive.positiveButtonProps = {
          ...dialogReactive.positiveButtonProps,
          loading: false
        }
        dialogReactive.negativeButtonProps = {
          ...dialogReactive.negativeButtonProps,
          disabled: false
        }
      }
    }
  })
}

const handleUpgradeRemote = (app: Application) => {
  if (!app.pluginId) {
    message.error('插件ID不能为空')
    return
  }
  
  const targetVersion = app.pluginVersion || app.remoteVersion || '-'
  
  const dialogReactive = dialog.warning({
    title: '升级应用',
    content: `确定要将应用 "${app.applicationName}" 升级到版本 ${targetVersion} 吗？`,
    positiveText: '升级',
    negativeText: '取消',
    positiveButtonProps: {
      loading: false
    },
    negativeButtonProps: {
      disabled: false
    },
    onPositiveClick: async () => {
      dialogReactive.positiveButtonProps = {
        ...dialogReactive.positiveButtonProps,
        loading: true
      }
      dialogReactive.negativeButtonProps = {
        ...dialogReactive.negativeButtonProps,
        disabled: true
      }
      upgradeRemoteLoading.value[app.pluginId] = true
      message.info(`正在升级应用: ${app.applicationName}...`)
      try {
        // 升级逻辑：直接安装远程版本
        await applicationApi.installRemote({ pluginId: app.pluginId })
        
        message.success('应用升级成功，页面即将刷新...')
        
        setTimeout(() => {
          window.location.reload()
        }, 1500)
      } catch (error: any) {

      } finally {
        upgradeRemoteLoading.value[app.pluginId] = false
        dialogReactive.positiveButtonProps = {
          ...dialogReactive.positiveButtonProps,
          loading: false
        }
        dialogReactive.negativeButtonProps = {
          ...dialogReactive.negativeButtonProps,
          disabled: false
        }
      }
    }
  })
}

const handleShowUpgradeInfo = (app: Application) => {
  upgradeInfoApp.value = app
  showUpgradeInfoModal.value = true
}

const handleUpgradeFromInfo = () => {
  if (!upgradeInfoApp.value) return
  
  currentApplication.value = upgradeInfoApp.value
  upgradeForm.targetVersion = upgradeInfoApp.value.remoteVersion || ''
  showUpgradeInfoModal.value = false
  showUpgradeModal.value = true
}

const handleShowUpgradeLogs = async (app: Application) => {
  showUpgradeLogModal.value = true
  upgradeLogsLoading.value = true
  upgradeLogs.value = []
  try {
    const logs = await applicationApi.getUpgradeLogs(app.id)
    upgradeLogs.value = logs
  } catch (error: any) {

  } finally {
    upgradeLogsLoading.value = false
  }
}

// 降级相关方法
const handleRollback = async (app: Application) => {
  rollbackTargetApp.value = app
  rollbackForm.targetVersion = ''
  selectedRollbackLog.value = null
  rollbackVersionOptions.value = []
  showRollbackModal.value = true
  
  // 加载升级日志作为可选版本
  rollbackVersionsLoading.value = true
  try {
    const logs = await applicationApi.getUpgradeLogs(app.id)
    // 只显示成功的升级记录，并按时间倒序排列
    const successLogs = logs.filter(log => log.status === 'SUCCESS' && log.newVersion)
    
    // 去重版本号（可能有多次升级到同一版本）
    const versionMap = new Map<string, ApplicationUpgradeLog>()
    successLogs.forEach(log => {
      if (log.newVersion && !versionMap.has(log.newVersion)) {
        versionMap.set(log.newVersion, log)
      }
    })
    
    // 转换为下拉选项
    rollbackVersionOptions.value = Array.from(versionMap.entries()).map(([version, log]) => ({
      label: `${version} (${formatDateTime(log.createTime)})`,
      value: version
    }))
    
    // 保存日志数据用于显示详情
    upgradeLogs.value = logs
  } catch (error: any) {

  } finally {
    rollbackVersionsLoading.value = false
  }
}

const handleRollbackVersionChange = (version: string) => {
  // 查找选中版本的日志信息
  const log = upgradeLogs.value.find(l => l.newVersion === version && l.status === 'SUCCESS')
  selectedRollbackLog.value = log || null
}

const confirmRollback = async () => {
  if (!rollbackForm.targetVersion) {
    message.warning('请选择要降级到的版本')
    return false
  }
  
  if (!rollbackTargetApp.value) return false
  
  rollbackLoading.value = true
  try {
    await applicationApi.rollback(rollbackTargetApp.value.id, {
      targetVersion: rollbackForm.targetVersion
    })
    
    message.success('应用降级成功，页面即将刷新...')
    showRollbackModal.value = false
    
    // 延迟刷新页面，让用户看到成功提示
    setTimeout(() => {
      window.location.reload()
    }, 1500)
    
    return true
  } catch (error: any) {
    console.error('降级应用失败:', error)
    return false
  } finally {
    rollbackLoading.value = false
  }
}

// 重启应用
const handleRestart = (app: Application) => {
  const dialogReactive = dialog.warning({
    title: '重启应用',
    content: `确定要重启应用 "${app.applicationName}" 吗？重启过程中应用将暂时不可用。`,
    positiveText: '确定',
    negativeText: '取消',
    positiveButtonProps: {
      loading: false
    },
    negativeButtonProps: {
      disabled: false
    },
    onPositiveClick: async () => {
      dialogReactive.positiveButtonProps = {
        ...dialogReactive.positiveButtonProps,
        loading: true
      }
      dialogReactive.negativeButtonProps = {
        ...dialogReactive.negativeButtonProps,
        disabled: true
      }
      restartLoading.value = true
      try {
        await applicationApi.restart(app.id)
        message.success('应用重启成功')
        // 刷新列表
        await loadData()
      } catch (error: any) {

      } finally {
        restartLoading.value = false
        dialogReactive.positiveButtonProps = {
          ...dialogReactive.positiveButtonProps,
          loading: false
        }
        dialogReactive.negativeButtonProps = {
          ...dialogReactive.negativeButtonProps,
          disabled: false
        }
      }
    }
  })
}

// 上传应用包相关方法
const handleUploadClick = () => {
  uploadFile.value = null
  showUploadModal.value = true
}

const handleFileChange = (options: { fileList: UploadFileInfo[] }) => {
  if (options.fileList.length > 0) {
    uploadFile.value = options.fileList[0]
  } else {
    uploadFile.value = null
  }
}

const confirmUpload = async () => {
  if (!uploadFile.value || !uploadFile.value.file) {
    message.warning('请选择要上传的应用包文件')
    return false
  }

  // 验证文件类型
  if (!uploadFile.value.name.endsWith('.jar')) {
    message.error('只支持 .jar 格式的应用包文件')
    return false
  }

  uploading.value = true
  
  try {
    const metadataFormData = new FormData()
    metadataFormData.append('file', uploadFile.value.file)
    const metadata = await applicationApi.getUploadInstallConfigMetadata(metadataFormData)

    if (metadata && metadata.length > 0) {
      showUploadModal.value = false
      openInstallConfigModal({ mode: 'upload', file: uploadFile.value }, metadata)
      return false
    }

    await executeUploadInstall(uploadFile.value)
    return true
  } catch (error: any) {
    console.error('上传安装应用失败:', error)
    message.error(error?.message || '上传安装失败')
    return false
  } finally {
    uploading.value = false
  }
}

const executeUploadInstall = async (
  fileInfo: UploadFileInfo,
  installConfig: Record<string, any> = {}
) => {
  if (!fileInfo.file) {
    throw new Error('上传文件不存在')
  }

  const formData = new FormData()
  formData.append('file', fileInfo.file)
  if (Object.keys(installConfig).length > 0) {
    formData.append('installConfig', JSON.stringify(installConfig))
  }

  await applicationApi.uploadAndInstall(formData)

  const fileName = fileInfo.name || ''
  const isIamPackage = fileName.includes('iam')
  if (isIamPackage) {
    message.success('IAM 应用包安装成功，首次初始化已触发。请留意服务日志中的“IAM 首次初始化完成”摘要，页面即将刷新...')
  } else {
    message.success('应用包上传并安装成功，页面即将刷新...')
  }
  showInstallConfigModal.value = false
  showUploadModal.value = false
  uploadFile.value = null

  setTimeout(() => {
    window.location.reload()
  }, 1500)
}

const confirmInstallWithConfig = async () => {
  if (!installConfigContext.value) {
    return false
  }

  if (installConfigMetadata.value.length > 0) {
    const valid = await installConfigFormRendererRef.value?.validate?.()
    if (valid === false) {
      message.error('请先修正安装前配置项')
      return false
    }
  }

  installConfigSubmitting.value = true
  try {
    if (installConfigContext.value.mode === 'remote') {
      if (!installConfigContext.value.app) {
        message.error('缺少远程应用信息')
        return false
      }
      await executeRemoteInstall(installConfigContext.value.app, installConfigForm.value || {})
      showInstallConfigModal.value = false
      return true
    }

    if (!installConfigContext.value.file) {
      message.error('缺少上传文件信息')
      return false
    }

    await executeUploadInstall(installConfigContext.value.file, installConfigForm.value || {})
    return true
  } catch (error: any) {
    console.error('带安装前配置的安装失败:', error)
    return false
  } finally {
    installConfigSubmitting.value = false
  }
}

const formatFileSize = (bytes: number): string => {
  if (bytes === 0) return '0 B'
  const k = 1024
  const sizes = ['B', 'KB', 'MB', 'GB']
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  return Math.round(bytes / Math.pow(k, i) * 100) / 100 + ' ' + sizes[i]
}

/** 扩展配置里布尔可能是 JSON 布尔或字符串 */
function boolFromConfigValue(v: unknown): boolean {
  if (v === true || v === 1) return true
  if (v === false || v === 0 || v == null || v === '') return false
  if (typeof v === 'string') {
    const s = v.trim().toLowerCase()
    return s === 'true' || s === '1' || s === 'yes'
  }
  return false
}

// 应用配置相关方法
const handleShowConfig = async (app: Application) => {
  configTargetApp.value = app
  configMetadata.value = []
  
  // 加载配置元数据
  configMetadataLoading.value = true
  try {
    const metadata = await applicationApi.getConfigMetadata(app.id)
    configMetadata.value = metadata || []
    console.log('[ApplicationManagement] 加载配置元数据:', {
      appId: app.id,
      appName: app.applicationName,
      metadataCount: configMetadata.value.length
    })
  } catch (error: any) {

    configMetadata.value = []
  } finally {
    configMetadataLoading.value = false
  }
  
  // 加载当前配置
  configLoading.value = true
  try {
    const config = await applicationApi.getConfig(app.id)
    const ext = config.extensionConfig || {}
    configForm.surfaceAdmin = boolFromConfigValue(ext.surfaceAdmin)
    configForm.surfaceConsumer = boolFromConfigValue(ext.surfaceConsumer)
    configForm.autoLoadAdmin = boolFromConfigValue(config.autoLoadAdmin ?? ext.autoLoadAdmin)
    configForm.autoLoadConsumer = boolFromConfigValue(config.autoLoadConsumer ?? ext.autoLoadConsumer)
    configForm.loadOnStartup = boolFromConfigValue(config.loadOnStartup)
    configForm.startPriority = config.startPriority || 50
    configForm.startDelay = config.startDelay || 0
    configForm.description = config.description || ''
    configForm.extensionConfig = config.extensionConfig || {}
    
    console.log('[ApplicationManagement] 加载应用配置:', {
      appId: app.id,
      appName: app.applicationName,
      extensionConfig: configForm.extensionConfig,
      extensionConfigKeys: Object.keys(configForm.extensionConfig)
    })
  } catch (error: any) {
    console.error('加载应用配置失败:', error)
    // 使用默认值
    configForm.surfaceAdmin = false
    configForm.surfaceConsumer = false
    configForm.autoLoadAdmin = false
    configForm.autoLoadConsumer = false
    configForm.loadOnStartup = false
    configForm.startPriority = 50
    configForm.startDelay = 0
    configForm.description = ''
    configForm.extensionConfig = {}
  } finally {
    configLoading.value = false
  }
  
  configCollapseExpanded.value = ['advanced']
  showConfigModal.value = true
}

const confirmConfig = async () => {
  if (!configTargetApp.value) return false
  
  configLoading.value = true
  try {
    // 获取最新的 extensionConfig 值
    const extensionConfigToSave = { ...configForm.extensionConfig }
    extensionConfigToSave.surfaceAdmin = configForm.surfaceAdmin
    extensionConfigToSave.surfaceConsumer = configForm.surfaceConsumer
    
    console.log('[ApplicationManagement] 保存配置:', {
      appId: configTargetApp.value.id,
      appName: configTargetApp.value.applicationName,
      extensionConfig: extensionConfigToSave,
      extensionConfigKeys: Object.keys(extensionConfigToSave)
    })
    
    await applicationApi.updateConfig(configTargetApp.value.id, {
      autoLoadAdmin: configForm.autoLoadAdmin,
      autoLoadConsumer: configForm.autoLoadConsumer,
      loadOnStartup: configForm.loadOnStartup,
      startPriority: configForm.startPriority,
      startDelay: configForm.startDelay,
      description: configForm.description,
      extensionConfig: extensionConfigToSave
    })
    
    message.success('应用配置已保存')
    showConfigModal.value = false
    return true
  } catch (error: any) {
    console.error('保存应用配置失败:', error)
    // 错误消息已在 request.ts 中显示，这里不再重复显示
    return false
  } finally {
    configLoading.value = false
  }
}

// Helper Functions
const getMoreActions = (app: Application) => {
  const actions: Array<{ label: string; key: string; disabled?: boolean }> = []
  
  // 升级日志
  actions.push({
    label: '升级日志',
    key: 'upgrade-logs'
  })
  
  // 降级
  actions.push({
    label: '降级',
    key: 'rollback',
    disabled: app.applicationType === 'integrated' || app.aggregateApp
  })
  
  // 详情
  actions.push({
    label: '详情',
    key: 'detail'
  })
  
  // 启动/停止
  if (app.status === 1) {
    actions.push({
      label: '停止',
      key: 'stop'
    })
  } else {
    actions.push({
      label: '启动',
      key: 'start'
    })
  }
  
  // 卸载
  actions.push({
    label: '卸载',
    key: 'uninstall',
    disabled: app.isDefault === 1 || app.applicationType === 'integrated' || app.aggregateApp
  })
  
  return actions
}

const handleMoreAction = (key: string, app: Application) => {
  switch (key) {
    case 'upgrade-logs':
      handleShowUpgradeLogs(app)
      break
    case 'rollback':
      handleRollback(app)
      break
    case 'detail':
      handleViewDetail(app)
      break
    case 'start':
      handleStart(app)
      break
    case 'stop':
      handleStop(app)
      break
    case 'uninstall':
      handleUninstall(app)
      break
  }
}

const getApplicationTypeColor = (type: ApplicationType): 'default' | 'success' | 'warning' | 'error' | 'info' => {
  if (type === 'integrated') {
    return 'info'
  } else if (type === 'plugin') {
    return 'success'
  } else if (type === 'aggregated') {
    return 'warning'
  }
  return 'default'
}

const getPluginTypes = (pluginType: string): string[] => {
  if (!pluginType) return []
  return pluginType.split(',').map(t => t.trim()).filter(t => t)
}

const getPluginTypeText = (type: string): string => {
  const typeMap: Record<string, string> = {
    'TRIGGER': '触发器',
    'TASK': '任务',
    'APPLICATION': '应用',
    'EXECUTOR': '执行器',
    'DATASOURCE': '数据源'
  }
  return typeMap[type.toUpperCase()] || type
}

const getPluginTypeColor = (type: string): 'default' | 'success' | 'warning' | 'error' | 'info' => {
  const colorMap: Record<string, 'default' | 'success' | 'warning' | 'error' | 'info'> = {
    'TRIGGER': 'warning',
    'TASK': 'success',
    'APPLICATION': 'info',
    'EXECUTOR': 'error',
    'DATASOURCE': 'default'
  }
  return colorMap[type.toUpperCase()] || 'default'
}

const formatDateTime = (dateTime: string): string => {
  if (!dateTime) return '-'
  const date = new Date(dateTime)
  return date.toLocaleString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit'
  })
}

// 获取操作类型颜色
const getOperationTypeColor = (type: string): 'default' | 'success' | 'warning' | 'error' | 'info' => {
  const colorMap: Record<string, 'default' | 'success' | 'warning' | 'error' | 'info'> = {
    'START': 'success',
    'STOP': 'warning',
    'RESTART': 'info',
    'INSTALL': 'success',
    'UNINSTALL': 'error',
    'UPGRADE': 'warning',
    'ROLLBACK': 'warning',
    'CONFIG_UPDATE': 'info'
  }
  return colorMap[type] || 'default'
}

// 格式化 JSON
const formatJson = (jsonStr: string | null): string => {
  if (!jsonStr) return '{}'
  try {
    const obj = typeof jsonStr === 'string' ? JSON.parse(jsonStr) : jsonStr
    return JSON.stringify(obj, null, 2)
  } catch (e) {
    return jsonStr
  }
}

async function loadBizTags(typeKey?: string) {
  try {
    // 默认业务标签类型为 plugin_biz_type；当左侧选中类型时透传 key，便于后端按类型扩展
    bizTags.value = await applicationApi.getTags(typeKey || 'plugin_biz_type')
  } catch {
    bizTags.value = []
  }
}

// Lifecycle
onMounted(async () => {
  try {
    categories.value = await applicationApi.getCategories()
  } catch {
    categories.value = []
  }
  await loadBizTags()
  loadData()
})
</script>

<style scoped>
.application-management-page {
  height: 100%;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  background: #f5f5f5;
}

.page-content {
  flex: 1;
  overflow-y: auto;
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 20px;
}

/* 加载和空状态 */
:deep(.loading-state) {
  display: flex;
  align-items: center;
  justify-content: center;
  flex: 1;
  padding: 60px;
}

:deep(.empty-state) {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  flex: 1;
  padding: 60px;
  text-align: center;
  color: #999;
  gap: 16px;
}

:deep(.empty-state__icon) {
  opacity: 0.5;
}

:deep(.empty-state__text) {
  font-size: 14px;
}

/* 应用列表 */
:deep(.app-list) {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(260px, 1fr));
  gap: 16px;
  margin-top: 0;
}

:deep(.app-card) {
  transition: all 0.3s ease;
  cursor: pointer;
  position: relative;
  z-index: 1;
  display: flex;
  flex-direction: column;
}

:deep(.app-card) :deep(.n-card) {
  height: 100%;
  display: flex;
  flex-direction: column;
}

:deep(.app-card:hover) {
  transform: translateY(-4px);
  box-shadow: 0 8px 16px rgba(0, 0, 0, 0.1);
  z-index: 2;
}

:deep(.app-card) :deep(.n-card__content) {
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 12px;
  height: 100%;
}

/* 应用卡片头部 */
:deep(.app-header) {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-shrink: 0;
}

:deep(.app-icon) {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 44px;
  height: 44px;
  border-radius: 8px;
  flex-shrink: 0;
}

:deep(.app-icon--integrated) {
  background: rgba(99, 102, 241, 0.1);
  color: #6366f1;
}

:deep(.app-icon--plugin) {
  background: rgba(16, 185, 129, 0.1);
  color: #10b981;
}

:deep(.app-icon--aggregated) {
  background: rgba(245, 158, 11, 0.12);
  color: #d97706;
}

:deep(.app-icon__img) {
  width: fit-content;
  max-width: 36px;
  max-height: 36px;
  object-fit: contain;
  border-radius: 6px;
}

:deep(.app-info) {
  flex: 1;
  min-width: 0;
}

:deep(.app-name) {
  font-size: 15px;
  font-weight: 600;
  color: #1f2937;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  margin-bottom: 2px;
  display: flex;
  align-items: center;
}

:deep(.app-code) {
  font-size: 12px;
  color: #6b7280;
  font-family: monospace;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

:deep(.app-status) {
  flex-shrink: 0;
}

/* 应用卡片主体 */
:deep(.app-body) {
  display: flex;
  flex-direction: column;
  gap: 12px;
  flex: 1;
}

:deep(.app-meta) {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

:deep(.meta-item) {
  font-size: 13px;
  color: #6b7280;
  display: flex;
  align-items: center;
  gap: 4px;
}

:deep(.meta-label) {
  color: #9ca3af;
}

:deep(.meta-value) {
  color: #1f2937;
  font-family: monospace;
  font-size: 12px;
}

:deep(.app-description) {
  font-size: 13px;
  color: #6b7280;
  line-height: 1.5;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  text-overflow: ellipsis;
}

/* 应用卡片底部 */
:deep(.app-footer) {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-top: 8px;
  border-top: 1px solid #e5e7eb;
  font-size: 12px;
  color: #9ca3af;
  margin-top: auto;
  flex-shrink: 0;
}

:deep(.app-time) {
  display: flex;
  align-items: center;
  gap: 4px;
}

:deep(.app-actions) {
  display: flex;
  align-items: center;
  gap: 8px;
}

/* 分页 */
:deep(.pagination) {
  display: flex;
  justify-content: flex-end;
  padding: 16px 0;
  margin-top: 8px;
}

/* 详情抽屉 */
.app-detail {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.detail-section {
  margin-bottom: 16px;
}

/* 响应式设计 */
@media (max-width: 768px) {
  .page-content {
    padding: 12px;
  }

  :deep(.app-list) {
    grid-template-columns: 1fr;
  }
}

/* 升级信息弹窗 */
.upgrade-info {
  padding: 4px 0;
}

.upgrade-info :deep(.n-descriptions) {
  margin-top: 0;
}

.upgrade-info :deep(.n-descriptions-item-label) {
  font-weight: 500;
}

.upgrade-log-list {
  max-height: 480px;
  overflow-y: auto;
  padding-right: 4px;
}

.upgrade-log-item {
  margin-top: 4px;
  font-size: 13px;
  color: #4b5563;
}

.upgrade-log-row {
  display: flex;
  gap: 4px;
  line-height: 1.6;
}

/* 配置对话框样式 */
.config-modal-content {
  display: flex;
  flex-direction: column;
  gap: 16px;
  padding-right: 8px; /* 为滚动条留出空间 */
}

.app-info-card {
  background: #f8f9fa;
}

.app-info-row {
  display: flex;
  gap: 32px;
  flex-wrap: wrap;
}

.app-info-item {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
}

.info-label {
  color: #6b7280;
  font-weight: 500;
}

.info-value {
  color: #1f2937;
  font-weight: 600;
}

.config-section {
  margin-bottom: 0;
}

.config-advanced-collapse :deep(.n-collapse-item__header) {
  font-weight: 600;
}

.config-surface-card {
  margin-bottom: 20px;
}

.config-surface-card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  width: 100%;
}

.config-surface-card-title {
  font-size: 14px;
  font-weight: 600;
}

.surface-preload-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px 18px;
}

.surface-preload-item {
  padding: 14px 14px 12px;
  border-radius: 8px;
  border: 1px solid var(--n-border-color);
  background: var(--n-color-modal);
}

.surface-preload-label {
  font-weight: 600;
  font-size: 13px;
  margin-bottom: 10px;
  color: var(--n-text-color);
}

.surface-preload-desc {
  margin-top: 10px;
  font-size: 12px;
  line-height: 1.5;
  color: var(--n-text-color-3);
}

.advanced-config-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 16px 24px;
  margin-bottom: 16px;
}

.advanced-config-grid--startup {
  margin-top: 4px;
}

.advanced-config-grid :deep(.n-form-item) {
  margin-bottom: 0;
}

.advanced-config-grid :deep(.n-form-item-label) {
  font-size: 13px;
}

/* 滚动条样式优化 */
:deep(.n-scrollbar-rail) {
  right: 0;
}

:deep(.n-scrollbar-rail__scrollbar) {
  width: 6px;
  border-radius: 3px;
}

/* 操作日志样式 */
.operation-log-list {
  padding: 16px 0;
}

.log-header {
  display: flex;
  align-items: center;
  font-size: 14px;
}

.log-content {
  margin-top: 8px;
  font-size: 13px;
}

.log-row {
  display: flex;
  gap: 8px;
  margin-bottom: 4px;
  line-height: 1.6;
}

.log-label {
  color: #9ca3af;
  font-weight: 500;
  min-width: 60px;
}

.log-value {
  color: #1f2937;
  flex: 1;
}

.config-diff {
  margin-top: 12px;
}

.config-compare {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
  margin-top: 8px;
}

.config-column {
  border: 1px solid #e5e7eb;
  border-radius: 4px;
  overflow: hidden;
}

.config-title {
  background: #f3f4f6;
  padding: 8px 12px;
  font-weight: 600;
  font-size: 13px;
  color: #374151;
  border-bottom: 1px solid #e5e7eb;
}

.config-content {
  padding: 12px;
  margin: 0;
  font-size: 12px;
  font-family: 'Monaco', 'Menlo', 'Ubuntu Mono', monospace;
  background: #fafafa;
  overflow-x: auto;
  max-height: 300px;
  overflow-y: auto;
}

@media (max-width: 768px) {
  .advanced-config-grid {
    grid-template-columns: 1fr;
  }
  
  .config-modal-content {
    padding-right: 4px;
  }
  
  .config-compare {
    grid-template-columns: 1fr;
  }
}
</style>
