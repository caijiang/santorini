{{/*
通用标签，但是没有 app.kubernetes.io/name
*/}}
{{- define "common.labels.withoutName" -}}
{{- if and (hasKey . "customLabels") (hasKey . "context") -}}
{{- $default := dict "app.kubernetes.io/name" (include "common.names.name" .context) "helm.sh/chart" (include "common.names.chart" .context) "app.kubernetes.io/instance" .context.Release.Name "app.kubernetes.io/managed-by" .context.Release.Service -}}
{{- with .context.Chart.AppVersion -}}
{{- $_ := set $default "app.kubernetes.io/version" . -}}
{{- end -}}
{{ template "common.tplvalues.merge" (dict "values" (list .customLabels $default) "context" .context) }}
{{- else -}}
helm.sh/chart: {{ include "common.names.chart" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
{{- with .Chart.AppVersion }}
app.kubernetes.io/version: {{ . | replace "+" "_" | quote }}
{{- end -}}
{{- end -}}
{{- end -}}

{{/* 定义一个 issuer 负责颁发我们的 ingress 证书 */}}
{{- define "santorini.issuer" -}}
{{- printf "%s-common-letsencrypt-issuer" (include "common.names.fullname" .) }}
{{- end }}

{{/*定义一个变量表示对应的 管理员邮箱地址*/}}
{{- define "santorini.masterEmail" -}}
{{- if .Values.masterEmail -}}
{{- .Values.masterEmail -}}
{{- else -}}
{{- printf "manager@%s" (required "domain 必须填写/ domain is required." .Values.domain) }}
{{- end -}}
{{- end }}