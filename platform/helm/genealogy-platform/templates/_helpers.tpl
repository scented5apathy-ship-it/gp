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

{{/*
Merge per-environment namespace overrides into `baseline.namespaces`.

The chart stores the baseline namespace set as a map keyed by
namespace name. Per-environment values files can bump a quota
or change a podSecurity profile by adding an entry to
`baseline.namespacesOverrides.<ns>` without having to redefine
the entire list. This helper stages the merged map so Go
template `range` scope does not discard the mutation.

The function is invoked once per chart render via `include`
above the namespace template loop.
*/}}
{{- define "genealogy-platform.mergeNamespaceOverrides" -}}
{{- $root := . -}}
{{- $overrides := default dict $root.Values.baseline.namespacesOverrides -}}
{{- range $name, $override := $overrides -}}
{{- $existing := default dict (index $root.Values.baseline.namespaces $name) -}}
{{- $merged := mustMerge (deepCopy $existing) (deepCopy $override) -}}
{{- $_ := set $root.Values.baseline.namespaces $name $merged -}}
{{- end -}}
{{- end -}}

{{/*
Recursively merge two maps. Sprig's `merge` / `mustMerge` only
shallow-merge (sub-maps are replaced wholesale), but the
namespace override semantics require deep merge so that bumping
`quota.requestsCpu` does not wipe the other `quota` fields.

The output is rendered as YAML so the calling template can `fromYaml`
the result back into a map.
*/}}
{{- define "genealogy-platform.mergeMaps" -}}
{{- $base := .base -}}
{{- $override := .override -}}
{{- $result := deepCopy $base -}}
{{- range $k, $v := $override -}}
{{- if and (kindIs "map" $v) (kindIs "map" (index $result $k)) -}}
{{- $sub := dict "base" (index $result $k) "override" $v -}}
{{- $merged := include "genealogy-platform.mergeMaps" $sub | fromYaml -}}
{{- $_ := set $result $k $merged -}}
{{- else -}}
{{- $_ := set $result $k $v -}}
{{- end -}}
{{- end -}}
{{- toYaml $result -}}
{{- end -}}
