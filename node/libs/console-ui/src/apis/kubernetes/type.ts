import { ModelData } from '@kubernetes-models/base';

export interface NamespaceWithLabelSelectors {
  namespace?: string;
  name?: string;
  /**
   * 默认 santorini.io/manageable=true
   */
  labelSelectors?: string[];
}

export function toPodsLabelSelectors({
  name,
  labelSelectors,
}: NamespaceWithLabelSelectors): string {
  const s1 = name ? [`app.kubernetes.io/name=${name}`] : [];
  const s2 = labelSelectors ?? ['santorini.io/manageable=true'];
  // const s2 = labelSelectors ?? []; // 之前的版本没有给 pod添加这个 label
  return [...s1, ...s2].join(',');
}

export interface NamespacedNamedResource {
  namespace?: string;
  name?: string;
}

export interface ObjectContainer {
  namespace?: string;
  name?: string;
  yaml?: string;
  jsonObject?: any;
}

export interface TypedObjectContainer<T> extends ObjectContainer {
  data?: ModelData<T>;
}
