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
{{/*定义一个变量表示 santorini 控制台前端 名称*/}}
{{- define "common.santoriniConsoleFrontendName" -}}
{{- printf "%s-console-frontend" (include "common.santoriniName" .) }}
{{- end }}

{{/*定义一个变量表示 santorini nacos 名称*/}}
{{- define "common.santoriniNacosName" -}}
{{- printf "%s-nacos" (include "common.santoriniName" .) }}
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

{{/*定义一个模板 可以获取其host名称，如果未曾提供，则根据 */}}
{{- define "ingress.hostName" -}}
{{- $ctx := index . 1 -}}
{{- $defaultName := index . 2 -}}
{{- $root := index . 0 -}}
{{- $defaultHostName := printf "%s.%s" $defaultName $root.Values.domain -}}
{{- $ctx.host| default $defaultHostName -}}
{{- end -}}

{{- define "ingress.certManagerIssuerName" -}}
{{- $root := index . 0 -}}
{{- $ctx := index . 1 -}}
{{- $defaultIssuerName := (include "santorini.issuer" $root) -}}
{{- $issuerName := $ctx.issuerName | default (include "santorini.issuer" $root) -}}
{{- if not $ctx.skipIssue -}}
{{- printf "cert-manager.io/cluster-issuer: %s" $issuerName -}}
{{- end -}}
{{- end -}}

{{- define "ingress.secretName" -}}
{{- $ctx := index . 1 -}}
{{- $defaultName := index . 2 -}}
{{- $root := index . 0 -}}
{{- if $ctx.secretName -}}
{{- $ctx.secretName -}}
{{- else -}}
{{- printf "%s-tls" (include "ingress.hostName" (list $root $ctx $defaultName)) -}}
{{- end -}}
{{- end -}}

{{/*
Return the proper Nacos image name
*/}}
{{- define "nacos.image" -}}
{{- include "common.images.image" (dict "imageRoot" .Values.santorini.nacos.image "global" .Values.global) -}}
{{- end -}}

{{- define "backend.image" -}}
{{- include "common.images.image" (dict "imageRoot" .Values.santorini.backend.image "global" .Values.global) -}}
{{- end -}}

{{- define "frontend.image" -}}
{{- include "common.images.image" (dict "imageRoot" .Values.santorini.frontend.image "global" .Values.global) -}}
{{- end -}}
