{{/*
Build the canonical probe block applied to every workload.

Usage:
  probes:
    {{- include "genealogy-platform.probes" . | nindent 8 }}

Per design §13 the path contract is `/healthz/{live,ready,startup}` —
enforced by `libs/platform-spring-boot-starter`. The block is
templated so workload authors cannot drift from the contract.
*/}}
{{- define "genealogy-platform.probes" -}}
{{- $probes := .Values.baseline.probes -}}
startupProbe:
  httpGet:
    path: {{ $probes.paths.startup }}
    port: http
  failureThreshold: {{ $probes.startup.failureThreshold }}
  periodSeconds: {{ $probes.startup.periodSeconds }}
  timeoutSeconds: {{ $probes.startup.timeoutSeconds }}
livenessProbe:
  httpGet:
    path: {{ $probes.paths.live }}
    port: http
  failureThreshold: {{ $probes.liveness.failureThreshold }}
  periodSeconds: {{ $probes.liveness.periodSeconds }}
  timeoutSeconds: {{ $probes.liveness.timeoutSeconds }}
  initialDelaySeconds: {{ $probes.liveness.initialDelaySeconds }}
readinessProbe:
  httpGet:
    path: {{ $probes.paths.ready }}
    port: http
  failureThreshold: {{ $probes.readiness.failureThreshold }}
  periodSeconds: {{ $probes.readiness.periodSeconds }}
  timeoutSeconds: {{ $probes.readiness.timeoutSeconds }}
  initialDelaySeconds: {{ $probes.readiness.initialDelaySeconds }}
{{- end -}}
