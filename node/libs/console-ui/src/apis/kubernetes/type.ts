import { ModelData } from '@kubernetes-models/base';

export interface NamespaceWithLabelSelectors {
  namespace?: string;
  name?: string;
  /**
   * 默认 santorini.io/manageable=true
   */
  labelSelectors?: string[];
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
