<template>
  <div class="application-management-page">
    <!-- 页面头部 -->
    <PageHeader title="应用管理" subtitle="管理已安装的应用插件">
      <template #actions>
        <n-button v-if="activeTab === 'local'" type="primary" @click="handleUploadClick">
          <template #icon>
            <n-icon><component :is="CloudUploadOutline" /></n-icon>
          </template>
          上传应用包
        </n-button>
        <n-button :loading="refreshLoading" @click="loadData">
          <template #icon>
            <n-icon><component :is="Refresh" /></n-icon>
          </template>
          刷新
        </n-button>
      </template>
    </PageHeader>

    <div class="page-content">
      <!-- 标签页 -->
      <n-tabs v-model:value="activeTab" type="line" @update:value="handleTabChange">
        <n-tab-pane name="local" tab="我的应用">
          <!-- 过滤面板 -->
          <FilterPanel
            v-model:filters="filters"
            v-model:show-advanced="showAdvanced"
            :basic-fields="basicFields"
            @search="handleSearch"
            @reset="handleReset"
          />

          <!-- 应用列表 -->
          <div v-if="loading" class="loading-state">
            <n-spin size="large" />
          </div>

          <div v-else-if="tableData.length === 0" class="empty-state">
            <div class="empty-state__icon">
              <n-icon size="48">
                <component :is="AppsOutline" />
              </n-icon>
            </div>
            <div class="empty-state__text">
              {{ filters.keyword ? '未找到匹配的应用' : '暂无应用信息' }}
            </div>
          </div>

          <div v-else class="app-list">
            <n-card
              v-for="app in tableData"
              :key="app.id"
              class="app-card"
              hoverable
              @click="handleViewDetail(app)"
            >
              <div class="app-header">
                <div class="app-icon" :class="`app-icon--${app.applicationType}`">
                  <n-icon size="24">
                    <component :is="app.applicationType === 'integrated' ? CubeOutline : ExtensionPuzzleOutline" />
                  </n-icon>
                </div>
                <div class="app-info">
                  <div class="app-name">
                    {{ app.applicationName }}
                    <n-tag v-if="app.isDefault === 1" type="warning" size="small" style="margin-left: 8px">
                      默认
                    </n-tag>
                  </div>
                  <div class="app-code" >{{ app.applicationCode }}</div>
                </div>
                <div class="app-status">
                  <n-tag :type="app.status === 1 ? 'success' : 'error'" size="small">
                    {{ app.statusText }}
                  </n-tag>
                </div>
              </div>

              <div class="app-body">
                <div class="app-meta">
                  <div class="meta-item" v-copy="app.pluginId">
                    <span class="meta-label">插件ID：</span>
                    <span class="meta-value" >{{ app.pluginId }}</span>
                  </div>
                  <div class="meta-item">
                    <span class="meta-label">版本：</span>
                    <span class="meta-value">{{ app.pluginVersion || '-' }}</span>
                    <n-tooltip v-if="app.hasNewVersion" placement="top">
                      <template #trigger>
                        <n-icon 
                          size="16" 
                          color="#f59e0b" 
                          style="margin-left: 6px; cursor: pointer; vertical-align: middle;"
                          @click.stop="handleShowUpgradeInfo(app)"
                        >
                          <component :is="RocketOutline" />
                        </n-icon>
                      </template>
                      <span>有新版本 {{ app.remoteVersion }} 可升级</span>
                    </n-tooltip>
                  </div>
                  <div class="meta-item">
                    <span class="meta-label">类型：</span>
                    <n-tag size="small" :type="getApplicationTypeColor(app.applicationType)">
                      {{ app.applicationTypeText }}
                    </n-tag>
                  </div>
                  <div v-if="app.pluginType" class="meta-item">
                    <span class="meta-label">插件类型：</span>
                    <n-space :size="4">
                      <n-tag 
                        v-for="type in getPluginTypes(app.pluginType)" 
                        :key="type" 
                        size="small" 
                        :type="getPluginTypeColor(type)"
                      >
                        {{ getPluginTypeText(type) }}
                      </n-tag>
                    </n-space>
                  </div>
                </div>

                <div v-if="app.description" class="app-description">
                  {{ app.description }}
                </div>
              </div>

              <div class="app-footer">
                <div class="app-time">
                  <n-icon size="14"><component :is="TimeOutline" /></n-icon>
                  安装：{{ formatDateTime(app.installTime) }}
                </div>
                <div class="app-actions">
                  <!-- 配置按钮 -->
                  <n-button
                    text
                    type="primary"
                    size="small"
                    @click.stop="handleShowConfig(app)"
                  >
                    配置
                  </n-button>
                  
                  <!-- 升级按钮（有新版本时显示） -->
                  <n-button
                    v-if="app.hasNewVersion"
                    text
                    type="warning"
                    size="small"
                    :disabled="app.applicationType === 'integrated'"
                    @click.stop="handleUpgrade(app)"
                  >
                    升级
                  </n-button>
                  
                  <!-- 重启按钮 -->
                  <n-button
                    text
                    type="info"
                    size="small"
                    :disabled="app.status !== 1"
                    @click.stop="handleRestart(app)"
                  >
                    重启
                  </n-button>
                  
                  <!-- 更多操作下拉菜单 -->
                  <n-dropdown
                    :options="getMoreActions(app)"
                    @select="(key) => handleMoreAction(key, app)"
                    trigger="click"
                  >
                    <n-button text size="small" @click.stop>
                      <template #icon>
                        <n-icon><component :is="EllipsisHorizontalOutline" /></n-icon>
                      </template>
                    </n-button>
                  </n-dropdown>
                </div>
              </div>
            </n-card>
          </div>

          <!-- 分页 -->
          <div v-if="pagination.itemCount > 0" class="pagination">
            <n-pagination
              v-model:page="pagination.page"
              v-model:page-size="pagination.pageSize"
              :page-count="Math.ceil(pagination.itemCount / pagination.pageSize)"
              :page-sizes="pagination.pageSizes"
              show-size-picker
              @update:page="handlePageChange"
              @update:page-size="handlePageSizeChange"
            />
          </div>
        </n-tab-pane>

        <n-tab-pane name="remote" tab="应用商店">
          <!-- 远程应用过滤面板 -->
          <FilterPanel
            v-model:filters="remoteFilters"
            v-model:show-advanced="showRemoteAdvanced"
            :basic-fields="remoteBasicFields"
            @search="handleRemoteSearch"
            @reset="handleRemoteReset"
          />

          <!-- 远程应用列表 -->
          <div v-if="remoteLoading" class="loading-state">
            <n-spin size="large" />
          </div>

          <div v-else-if="remoteTableData.length === 0" class="empty-state">
            <div class="empty-state__icon">
              <n-icon size="48">
                <component :is="AppsOutline" />
              </n-icon>
            </div>
            <div class="empty-state__text">
              {{ remoteFilters.keyword ? '未找到匹配的应用' : '暂无远程应用' }}
            </div>
          </div>

          <div v-else class="app-list">
            <n-card
              v-for="app in remoteTableData"
              :key="app.id"
              class="app-card"
              hoverable
              @click="handleViewRemoteDetail(app)"
            >
              <div class="app-header">
                <div class="app-icon app-icon--plugin">
                  <n-icon size="24">
                    <component :is="ExtensionPuzzleOutline" />
                  </n-icon>
                </div>
                <div class="app-info">
                  <div class="app-name">
                    {{ app.applicationName }}
                  </div>
                  <div class="app-code">{{ app.applicationCode }}</div>
                </div>
                <div class="app-status">
                  <n-tag v-if="app.installStatus === 'INSTALLED'" type="success" size="small">
                    已安装
                  </n-tag>
                  <n-tag v-else-if="app.installStatus === 'UPGRADABLE'" type="warning" size="small">
                    可升级
                  </n-tag>
                  <n-tag v-else type="info" size="small">
                    未安装
                  </n-tag>
                </div>
              </div>

              <div class="app-body">
                <div class="app-meta">
                  <div class="meta-item">
                    <span class="meta-label">插件ID：</span>
                    <span class="meta-value">{{ app.pluginId }}</span>
                  </div>
                  <div class="meta-item">
                    <span class="meta-label">远程版本：</span>
                    <span class="meta-value">{{ app.pluginVersion || '-' }}</span>
                  </div>
                  <div v-if="app.localVersion" class="meta-item">
                    <span class="meta-label">本地版本：</span>
                    <span class="meta-value">{{ app.localVersion }}</span>
                  </div>
                  <div v-if="app.author" class="meta-item">
                    <span class="meta-label">作者：</span>
                    <span class="meta-value">{{ app.author }}</span>
                  </div>
                </div>

                <div v-if="app.description" class="app-description">
                  {{ app.description }}
                </div>
              </div>

              <div class="app-footer">
                <div class="app-time">
                  <n-icon size="14"><component :is="TimeOutline" /></n-icon>
                  更新：{{ formatDateTime(app.updateTime) }}
                </div>
                <div class="app-actions">
                  <n-button
                    text
                    type="info"
                    size="small"
                    @click.stop="handleViewRemoteDetail(app)"
                  >
                    详情
                  </n-button>
                  <n-button
                    v-if="app.installStatus === 'NOT_INSTALLED'"
                    text
                    type="success"
                    size="small"
                    :loading="installRemoteLoading[app.pluginId]"
                    @click.stop="handleInstallRemote(app)"
                  >
                    安装
                  </n-button>
                  <n-button
                    v-else-if="app.installStatus === 'UPGRADABLE'"
                    text
                    type="warning"
                    size="small"
                    :loading="upgradeRemoteLoading[app.pluginId]"
                    @click.stop="handleUpgradeRemote(app)"
                  >
                    升级
                  </n-button>
                  <n-button
                    v-else
                    text
                    type="default"
                    size="small"
                    disabled
                  >
                    已安装
                  </n-button>
                </div>
              </div>
            </n-card>
          </div>

          <!-- 远程应用分页 -->
          <div v-if="remotePagination.itemCount > 0" class="pagination">
            <n-pagination
              v-model:page="remotePagination.page"
              v-model:page-size="remotePagination.pageSize"
              :page-count="Math.ceil(remotePagination.itemCount / remotePagination.pageSize)"
              :page-sizes="remotePagination.pageSizes"
              show-size-picker
              @update:page="handleRemotePageChange"
              @update:page-size="handleRemotePageSizeChange"
            />
          </div>
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

            <!-- 基础配置（折叠面板） -->
            <n-collapse :default-expanded-names="configMetadata.length === 0 ? ['advanced'] : []" class="config-section">
              <n-collapse-item title="高级配置" name="advanced">
                <n-form ref="configFormRef" :model="configForm" label-placement="left" label-width="100" size="small">
                  <div class="advanced-config-grid">
                    <n-form-item label="自动加载" path="autoLoad">
                      <n-switch v-model:value="configForm.autoLoad" :disabled="configLoading" size="small">
                        <template #checked>开启</template>
                        <template #unchecked>关闭</template>
                      </n-switch>
                    </n-form-item>

                    <n-form-item label="启动时加载" path="loadOnStartup">
                      <n-switch 
                        v-model:value="configForm.loadOnStartup" 
                        :disabled="configLoading || !configForm.autoLoad"
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
                        :disabled="configLoading || !configForm.autoLoad"
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
                        :disabled="configLoading || !configForm.autoLoad"
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
                      <li>自动加载：系统启动时是否自动加载该插件</li>
                      <li>启动时加载：插件加载后是否立即启动</li>
                      <li>启动优先级：数值越大越先启动（0-100）</li>
                      <li>启动延迟：应用启动前的等待时间</li>
                    </ul>
                  </n-alert>
                </n-form>
              </n-collapse-item>
            </n-collapse>
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
import { ref, reactive, onMounted } from 'vue'
import { useMessage } from '@keqi.gress/plugin-bridge'
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

// FilterFieldConfig 类型定义
export type FilterFieldType = 'input' | 'select' | 'date' | 'date-range'

export type FilterFieldConfig = {
  key: string
  label?: string
  type?: FilterFieldType
  placeholder?: string
  options?: Array<{ label: string; value: unknown }>
  clearable?: boolean
  span?: number
  slotName?: string
  componentProps?: Record<string, unknown>
}

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

// 过滤器状态
const showAdvanced = ref(false)
const filters = ref({
  keyword: '',
  status: null as number | null,
  applicationType: null as ApplicationType | null
})

// 远程应用过滤器状态
const showRemoteAdvanced = ref(false)
const remoteFilters = ref({
  keyword: ''
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
  autoLoad: false,
  loadOnStartup: false,
  startPriority: 50,
  startDelay: 0,
  description: '',
  extensionConfig: {} as Record<string, any>
})
const configMetadata = ref<any[]>([])
const configMetadataLoading = ref(false)

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
      { label: '插件应用', value: 'plugin' }
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
  pagination.page = 1
  loadLocalData()
}

const handleRemoteSearch = () => {
  remotePagination.page = 1
  loadRemoteData()
}

const handleRemoteReset = () => {
  remoteFilters.value.keyword = ''
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
  currentApplication.value = app
  showDetailDrawer.value = true
}

const handleStart = (app: Application) => {
  confirmTarget.value = app
  showStartConfirm.value = true
}

const confirmStart = async () => {
  if (!confirmTarget.value) return false
  
  startLoading.value = true
  try {
    await applicationApi.enable(confirmTarget.value.id, 'admin')
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
    await applicationApi.disable(confirmTarget.value.id, 'admin')
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
      targetVersion: upgradeForm.targetVersion,
      operatorId: 'admin',
      operatorName: 'admin'
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
      operatorId: 'admin',
      operatorName: 'admin',
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

const handleInstallRemote = (app: Application) => {
  if (!app.pluginId) {
    message.error('插件ID不能为空')
    return
  }
  
  dialog.warning({
    title: '安装应用',
    content: `确定要安装应用 "${app.applicationName}" 吗？`,
    positiveText: '安装',
    negativeText: '取消',
    onPositiveClick: async () => {
      installRemoteLoading.value[app.pluginId] = true
      message.info(`正在安装应用: ${app.applicationName}...`)
      try {
        await applicationApi.installRemote(app.pluginId, 'admin')
        message.success('应用安装成功，页面即将刷新...')
        setTimeout(() => window.location.reload(), 1500)
      } catch (error: any) {

      } finally {
        installRemoteLoading.value[app.pluginId] = false
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
  
  dialog.warning({
    title: '升级应用',
    content: `确定要将应用 "${app.applicationName}" 升级到版本 ${targetVersion} 吗？`,
    positiveText: '升级',
    negativeText: '取消',
    onPositiveClick: async () => {
      upgradeRemoteLoading.value[app.pluginId] = true
      message.info(`正在升级应用: ${app.applicationName}...`)
      try {
        // 升级逻辑：直接安装远程版本
        await applicationApi.installRemote(app.pluginId, 'admin')
        
        message.success('应用升级成功，页面即将刷新...')
        
        setTimeout(() => {
          window.location.reload()
        }, 1500)
      } catch (error: any) {

      } finally {
        upgradeRemoteLoading.value[app.pluginId] = false
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
      targetVersion: rollbackForm.targetVersion,
      operatorId: 'admin',
      operatorName: 'admin'
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
  dialog.warning({
    title: '重启应用',
    content: `确定要重启应用 "${app.applicationName}" 吗？重启过程中应用将暂时不可用。`,
    positiveText: '确定',
    negativeText: '取消',
    onPositiveClick: async () => {
      restartLoading.value = true
      try {
        await applicationApi.restart(app.id, 'admin')
        message.success('应用重启成功')
        // 刷新列表
        await loadData()
      } catch (error: any) {

      } finally {
        restartLoading.value = false
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
    const formData = new FormData()
    formData.append('file', uploadFile.value.file)
    formData.append('operatorId', 'admin')
    formData.append('operatorName', 'admin')

    console.log('准备上传文件:', {
      fileName: uploadFile.value.name,
      fileSize: uploadFile.value.file?.size
    })

    await applicationApi.uploadAndInstall(formData)
    
    message.success('应用包上传并安装成功，页面即将刷新...')
    showUploadModal.value = false
    uploadFile.value = null
    
    // 延迟刷新页面，让用户看到成功提示
    setTimeout(() => {
      window.location.reload()
    }, 1500)
    
    return true
  } catch (error: any) {

    return false
  } finally {
    uploading.value = false
  }
}

const formatFileSize = (bytes: number): string => {
  if (bytes === 0) return '0 B'
  const k = 1024
  const sizes = ['B', 'KB', 'MB', 'GB']
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  return Math.round(bytes / Math.pow(k, i) * 100) / 100 + ' ' + sizes[i]
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
    configForm.autoLoad = config.autoLoad || false
    configForm.loadOnStartup = config.loadOnStartup || false
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
    configForm.autoLoad = false
    configForm.loadOnStartup = false
    configForm.startPriority = 50
    configForm.startDelay = 0
    configForm.description = ''
    configForm.extensionConfig = {}
  } finally {
    configLoading.value = false
  }
  
  showConfigModal.value = true
}

const confirmConfig = async () => {
  if (!configTargetApp.value) return false
  
  configLoading.value = true
  try {
    // 获取最新的 extensionConfig 值
    const extensionConfigToSave = { ...configForm.extensionConfig }
    
    console.log('[ApplicationManagement] 保存配置:', {
      appId: configTargetApp.value.id,
      appName: configTargetApp.value.applicationName,
      extensionConfig: extensionConfigToSave,
      extensionConfigKeys: Object.keys(extensionConfigToSave)
    })
    
    await applicationApi.updateConfig(configTargetApp.value.id, {
      autoLoad: configForm.autoLoad,
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
  const actions = []
  
  // 升级日志
  actions.push({
    label: '升级日志',
    key: 'upgrade-logs'
  })
  
  // 降级
  actions.push({
    label: '降级',
    key: 'rollback',
    disabled: app.applicationType === 'integrated'
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
    disabled: app.isDefault === 1 || app.applicationType === 'integrated'
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

// Lifecycle
onMounted(() => {
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
.loading-state {
  display: flex;
  align-items: center;
  justify-content: center;
  flex: 1;
  padding: 60px;
}

.empty-state {
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

.empty-state__icon {
  opacity: 0.5;
}

.empty-state__text {
  font-size: 14px;
}

/* 应用列表 */
.app-list {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(380px, 1fr));
  gap: 16px;
  margin-top: 12px;
}

.app-card {
  transition: all 0.3s ease;
  cursor: pointer;
  position: relative;
  z-index: 1;
  display: flex;
  flex-direction: column;
}

.app-card :deep(.n-card) {
  height: 100%;
  display: flex;
  flex-direction: column;
}

.app-card:hover {
  transform: translateY(-4px);
  box-shadow: 0 8px 16px rgba(0, 0, 0, 0.1);
  z-index: 2;
}

.app-card :deep(.n-card__content) {
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 12px;
  height: 100%;
}

/* 应用卡片头部 */
.app-header {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-shrink: 0;
}

.app-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 44px;
  height: 44px;
  border-radius: 8px;
  flex-shrink: 0;
}

.app-icon--integrated {
  background: rgba(99, 102, 241, 0.1);
  color: #6366f1;
}

.app-icon--plugin {
  background: rgba(16, 185, 129, 0.1);
  color: #10b981;
}

.app-info {
  flex: 1;
  min-width: 0;
}

.app-name {
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

.app-code {
  font-size: 12px;
  color: #6b7280;
  font-family: monospace;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.app-status {
  flex-shrink: 0;
}

/* 应用卡片主体 */
.app-body {
  display: flex;
  flex-direction: column;
  gap: 12px;
  flex: 1;
}

.app-meta {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.meta-item {
  font-size: 13px;
  color: #6b7280;
  display: flex;
  align-items: center;
  gap: 4px;
}

.meta-label {
  color: #9ca3af;
}

.meta-value {
  color: #1f2937;
  font-family: monospace;
  font-size: 12px;
}

.app-description {
  font-size: 13px;
  color: #6b7280;
  line-height: 1.5;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  text-overflow: ellipsis;
}

/* 应用卡片底部 */
.app-footer {
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

.app-time {
  display: flex;
  align-items: center;
  gap: 4px;
}

.app-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

/* 分页 */
.pagination {
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

  .app-list {
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

.advanced-config-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 16px 24px;
  margin-bottom: 16px;
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
