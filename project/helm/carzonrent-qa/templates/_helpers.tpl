{{- define "carzonrent-qa.name" -}}
{{- default .Chart.Name .Values.nameOverride | trunc 63 | trimSuffix "-" -}}
{{- end -}}

{{- define "carzonrent-qa.fullname" -}}
{{- if .Values.fullnameOverride -}}
{{- .Values.fullnameOverride | trunc 63 | trimSuffix "-" -}}
{{- else -}}
{{- $name := default .Chart.Name .Values.nameOverride -}}
{{- printf "%s-%s" .Release.Name $name | trunc 63 | trimSuffix "-" -}}
{{- end -}}
{{- end -}}

{{- define "carzonrent-qa.namespace" -}}
{{- default .Release.Namespace .Values.namespace.name -}}
{{- end -}}

{{- define "carzonrent-qa.labels" -}}
app.kubernetes.io/name: {{ include "carzonrent-qa.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
app.kubernetes.io/version: {{ .Chart.AppVersion | quote }}
app.kubernetes.io/component: qa-dashboard
app.kubernetes.io/part-of: carzonrent
app.kubernetes.io/managed-by: {{ .Release.Service }}
{{- end -}}

{{- define "carzonrent-qa.selectorLabels" -}}
app.kubernetes.io/name: {{ include "carzonrent-qa.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end -}}

{{- define "carzonrent-qa.serviceAccountName" -}}
{{- if .Values.serviceAccount.create -}}
{{- default (include "carzonrent-qa.fullname" .) .Values.serviceAccount.name -}}
{{- else -}}
{{- default "default" .Values.serviceAccount.name -}}
{{- end -}}
{{- end -}}
