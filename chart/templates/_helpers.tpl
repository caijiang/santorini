{{/*定义一个变量表示 santorini deployment 以及 serviceAccount 名称*/}}
{{- define "common.santoriniName" -}}
{{- printf "%s-santorini" (include "common.names.fullname" .) }}
{{- end }}

{{/*定义一个变量表示对应的 mysql 服务名称*/}}
{{- define "common.mysqlName" -}}
{{- printf "%s-mysql" (.Release.Name) }}
{{- end }}

{{/*定义一个变量表示 santorini 控制台后端 名称*/}}
{{- define "common.santoriniConsoleBackendName" -}}
{{- printf "%s-console-backend" (include "common.santoriniName" .) }}
{{- end }}

{{/*定义一个变量表示 santorini 人类角色中的管理员*/}}
{{- define "common.santoriniManager" -}}
{{- printf "%s-manager" (include "common.santoriniName" .) }}
{{- end }}
{{/*定义一个变量表示 santorini 人类角色中的一般操作员*/}}
{{- define "common.santoriniUser" -}}
{{- printf "%s-user" (include "common.santoriniName" .) }}
{{- end }}

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

{{/*
没有 app.kubernetes.io/name
*/}}
{{- define "common.matchLabels.withoutName" -}}
{{- if and (hasKey . "customLabels") (hasKey . "context") -}}
{{ merge (pick (include "common.tplvalues.render" (dict "value" .customLabels "context" .context) | fromYaml) "app.kubernetes.io/name" "app.kubernetes.io/instance") (dict "app.kubernetes.io/name" (include "common.names.name" .context) "app.kubernetes.io/instance" .context.Release.Name ) | toYaml }}
{{- else -}}
app.kubernetes.io/instance: {{ .Release.Name }}
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