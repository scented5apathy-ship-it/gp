{{/*
Expand a fully-qualified image reference.

Usage:
  {{ include "genealogy-platform.image" (dict "image" (dict "repository" "kong" "tag" "3.8.0" "registry" "docker.io")) }}

Returns:
  docker.io/kong:3.8.0
*/}}
{{- define "genealogy-platform.image" -}}
{{- $img := .image -}}
{{- $registry := default "docker.io" $img.registry -}}
{{- if $img.digest -}}
{{ $registry }}/{{ $img.repository }}@{{ $img.digest }}
{{- else -}}
{{ $registry }}/{{ $img.repository }}:{{ default "latest" $img.tag }}
{{- end -}}
{{- end -}}
