<template>
  <div class="panel">
    <h2 class="panel__title">上传新版本</h2>
    <p class="panel__desc">拖拽或选择插件包（.zip / .jar 占位），填写变更说明后提交。</p>

    <n-steps :current="step" class="steps" style="margin: 24px 0">
      <n-step title="选择包体" description="校验签名与体积" />
      <n-step title="元数据" description="版本号与说明" />
      <n-step title="确认提交" description="生成预览" />
    </n-steps>

    <n-card v-show="step === 1" title="1. 选择包体" size="small">
      <n-upload
        multiple
        directory-dnd
        :max="3"
        :default-upload="false"
        @change="onUploadChange"
      >
        <n-upload-dragger>
          <div class="dz">
            <div class="dz__icon">⬆</div>
            <p class="dz__t">点击或拖拽文件到此处</p>
            <p class="dz__s">单文件上限 100MB（Demo 不实际上传）</p>
          </div>
        </n-upload-dragger>
      </n-upload>
      <n-divider />
      <n-space justify="end">
        <n-button type="primary" :disabled="!fileNames.length" @click="step = 2">下一步</n-button>
      </n-space>
    </n-card>

    <n-card v-show="step === 2" title="2. 元数据" size="small">
      <n-form label-placement="left" label-width="96">
        <n-form-item label="版本号">
          <n-input v-model:value="meta.version" placeholder="例如 1.4.0" />
        </n-form-item>
        <n-form-item label="变更说明">
          <n-input v-model:value="meta.changelog" type="textarea" placeholder="列出兼容性、修复项…" :rows="4" />
        </n-form-item>
      </n-form>
      <n-space justify="end">
        <n-button @click="step = 1">上一步</n-button>
        <n-button type="primary" :disabled="!meta.version.trim()" @click="step = 3">下一步</n-button>
      </n-space>
    </n-card>

    <n-card v-show="step === 3" title="3. 确认" size="small">
      <n-descriptions bordered size="small" :column="1">
        <n-descriptions-item label="文件">{{ fileNames.join(', ') || '—' }}</n-descriptions-item>
        <n-descriptions-item label="版本">{{ meta.version }}</n-descriptions-item>
        <n-descriptions-item label="说明">{{ meta.changelog || '—' }}</n-descriptions-item>
      </n-descriptions>
      <n-space justify="end" style="margin-top: 16px">
        <n-button @click="step = 2">上一步</n-button>
        <n-button type="primary" :loading="submitting" @click="submit">模拟提交</n-button>
      </n-space>
    </n-card>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import {
  NButton,
  NCard,
  NDescriptions,
  NDescriptionsItem,
  NDivider,
  NForm,
  NFormItem,
  NInput,
  NSpace,
  NStep,
  NSteps,
  NUpload,
  NUploadDragger,
  useMessage
} from 'naive-ui'
import type { UploadFileInfo } from 'naive-ui'

const message = useMessage()
const step = ref(1)
const fileNames = ref<string[]>([])
const submitting = ref(false)
const meta = ref({ version: '', changelog: '' })

function onUploadChange(options: { fileList: UploadFileInfo[] }) {
  fileNames.value = options.fileList.map((f) => f.name).filter(Boolean) as string[]
}

async function submit() {
  submitting.value = true
  try {
    await new Promise((r) => setTimeout(r, 600))
    message.success('已模拟提交：审核队列 #D-1024')
    step.value = 1
    fileNames.value = []
    meta.value = { version: '', changelog: '' }
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped lang="scss">
@import '../../../styles/developer-portal-tokens.scss';

.panel__title {
  font-size: 1.25rem;
  font-weight: 700;
  color: var(--asdp-ink);
}

.panel__desc {
  margin-top: 8px;
  font-size: 14px;
  color: var(--asdp-ink-muted);
  line-height: 1.55;
}

.dz {
  padding: 12px 0;
}

.dz__icon {
  font-size: 2rem;
  margin-bottom: 8px;
  opacity: 0.7;
}

.dz__t {
  font-weight: 600;
  margin-bottom: 4px;
}

.dz__s {
  font-size: 12px;
  color: var(--asdp-ink-muted);
}
</style>
