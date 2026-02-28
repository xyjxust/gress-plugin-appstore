export type MiddlewareStatus =
  | 'NOT_INSTALLED'
  | 'INSTALLING'
  | 'RUNNING'
  | 'STOPPED'
  | 'ERROR'
  | 'UNKNOWN'

export interface MiddlewareInfo {
  id: string
  name?: string
  version?: string
  category?: string
  shared?: boolean
  serviceHost?: string
  servicePort?: number
  healthCheckUrl?: string
  status?: MiddlewareStatus
  workDir?: string
  config?: Record<string, any>
}

export interface MiddlewareInstallResult {
  middlewareId: string
  version?: string
  workDir?: string
  message?: string
}

export interface HealthCheckResult {
  healthy: boolean
  message?: string
  timestamp?: number
}

