{{/* vim: set filetype=mustache: */}}
{{/*
Kubernetes standard labels
we ovveride the standard labels defined in the _labels.tpl file in the dependecy chart
except no chart version label to prevent redis from restarts on every upgrade
*/}}

{{- define "call-nested" -}}
{{- $dot := index . 0 -}}
{{- $subchart := index . 1 -}}
{{- $template := index . 2 -}}
{{- include $template (dict "Chart" ($dot.Chart) "Values" (index $dot.Values $subchart) "Release" $dot.Release "Capabilities" $dot.Capabilities) -}}
{{- end -}}
