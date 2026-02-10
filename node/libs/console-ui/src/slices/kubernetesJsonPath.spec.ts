import { describe, expect } from 'vitest';
import jsonObject from './test-deployment.spec.json';

import {
  readKubernetesField,
  removeKubernetesField,
  updateKubernetesField,
} from './kubernetesJsonPath';

describe('k8s服务端应用相关', async () => {
  it('readKubernetesField', { skip: false }, async () => {
    expect(
      readKubernetesField(
        jsonObject,
        '.spec.template.spec.containers[name="main"].imagePullPolicy'
      )
    ).eq('IfNotPresent');

    expect(
      readKubernetesField(
        jsonObject,
        '.spec.template.spec.containers[name="main"].env[name="kubenamespace"].valueFrom.fieldRef.fieldPath'
      )
    ).eq('metadata.namespace');
  });
  it('removeKubernetesField', async () => {
    const obj = {
      ...jsonObject,
    };
    removeKubernetesField(
      obj,
      '.spec.template.spec.containers[name="main"].imagePullPolicy'
    );
    expect(
      readKubernetesField(
        obj,
        '.spec.template.spec.containers[name="main"].imagePullPolicy'
      )
    ).eq(undefined);

    removeKubernetesField(
      obj,
      '.spec.template.spec.containers[name="main"].env[name="kubenamespace"]'
    );
    expect(
      readKubernetesField(
        obj,
        '.spec.template.spec.containers[name="main"].env[name="kubenamespace"].valueFrom.fieldRef.fieldPath'
      )
    ).eq(undefined);

    updateKubernetesField(
      obj,
      '.spec.template.spec.containers[name="main"].imagePullPolicy',
      'fine'
    );
    expect(
      readKubernetesField(
        obj,
        '.spec.template.spec.containers[name="main"].imagePullPolicy'
      )
    ).eq('fine', '简单字段映射');
    updateKubernetesField(
      obj,
      '.spec.template.spec.containers[name="main"].env[name="kubenamespace"].valueFrom.fieldRef.fieldPath',
      'fine2'
    );
    expect(
      readKubernetesField(
        obj,
        '.spec.template.spec.containers[name="main"].env[name="kubenamespace"].valueFrom.fieldRef.fieldPath'
      )
    ).eq('fine2', '复杂映射');
  });
});
