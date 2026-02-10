import {useDispatch, useSelector} from 'react-redux';
import {
  autoMergeFieldManagers,
  ConflictFieldSolution,
  serverSideApplySlice,
  ServerSideApplyState,
} from '../../slices/serverSideApply';
import {ProForm, ProFormDependency, ProFormRadio,} from '@ant-design/pro-components';
import * as React from 'react';
import {useMemo} from 'react';
import _ from 'lodash';
import {Col, Drawer, Row, Tooltip, Typography} from 'antd';
import PreviewRevision from './PreviewRevision';
import {readKubernetesField} from '../../slices/kubernetesJsonPath';

/**
 * 负责纠正服务端应用时的冲突，所以还得需要知道原始 yaml,以及获取当前yaml的方式
 */
export default () => {
  const a = useSelector(
    (it) => (it as { serverSideApply: ServerSideApplyState }).serverSideApply
  );
  return <SSADrawer state={a} />;
};

interface Field {
  current: any;
  draft: any;
}

export const SSADrawer: React.FC<{ state: ServerSideApplyState }> = ({
  state,
}) => {
  const open = useMemo(() => {
    // 什么是 open? 没有关掉 也没有确认掉所有选择
    // 每个字段都选择: 放弃,保留
    return !!(
      state.originFields &&
      _.keys(state.originFields).length > 0 &&
      !state.userAbandon &&
      !state.solutions
    );
  }, [state.originFields, state.userAbandon, state.solutions]);
  const originFields = useMemo(() => {
    if (!state.originFields) return {};
    return state.originFields;
  }, [state.originFields]);
  const dispatch = useDispatch();
  // 提取本次提交: 当前值
  const fieldNames = useMemo(() => {
    if (!state.originFields) return [];
    return _.keys(state.originFields);
  }, [state.originFields]);
  const fields = useMemo(() => {
    // 当前值
    if (!state.initArgs?.current) {
      return {};
    }
    // 草稿纸
    if (!state.initArgs?.jsonObject) {
      return {};
    }
    const current = state.initArgs.current;
    const draft = state.initArgs.jsonObject;
    return _.fromPairs(
      fieldNames.map((fieldName) => {
        const c = readKubernetesField(current, fieldName);
        const d = readKubernetesField(draft, fieldName);
        return [
          fieldName,
          {
            current: c,
            draft: d,
          } as Field,
        ];
      })
    );
  }, [fieldNames, state.initArgs]);

  return (
    <Drawer
      open={open}
      title={'资源冲突字段合并'}
      onClose={() => dispatch(serverSideApplySlice.actions.abandon())}
      size={'large'}
    >
      <Typography.Paragraph>
        与其他管理员提交的字段发生冲突。
        <ul>
          <li>覆盖：强制使用提交数据</li>
          <li>合并：使用当前值作为本次提交</li>
          <li>放弃：放弃对这个字段的管理，字段将继续使用当前值</li>
        </ul>
      </Typography.Paragraph>
      {open && (
        <ProForm
          onFinish={async (vs) => {
            dispatch(serverSideApplySlice.actions.submit(vs));
            return true;
          }}
          initialValues={_.fromPairs(
            fieldNames
              .filter((it) =>
                originFields[it].some((o) =>
                  autoMergeFieldManagers.some((m) => m == o)
                )
              )
              .map((it) => [it, 'force'])
          )}
        >
          {fieldNames.map((name, index) => (
            <React.Fragment key={name}>
              <Row>
                <Col>
                  <Tooltip
                    title={`与此冲突管理员有:${originFields[name].join(',')}`}
                  >
                    <Typography.Title level={5}>{name}</Typography.Title>
                  </Tooltip>
                </Col>
              </Row>
              {index == 0 && (
                <Row>
                  <Col span={12} style={{ textAlign: 'center' }}>
                    <Typography.Text strong>当前</Typography.Text>
                  </Col>
                  <Col span={12} style={{ textAlign: 'center' }}>
                    <Typography.Text strong>提交</Typography.Text>
                  </Col>
                </Row>
              )}
              <ProFormDependency name={[name]}>
                {(inputs) => {
                  const solution = inputs[name] as
                    | ConflictFieldSolution
                    | undefined;
                  return (
                    <Row>
                      <Col span={12}>
                        <PreviewRevision
                          type={'current'}
                          value={fields[name].current}
                          solution={solution}
                        />
                      </Col>
                      <Col span={12}>
                        <PreviewRevision
                          type={'draft'}
                          value={fields[name].draft}
                          solution={solution}
                        />
                      </Col>
                    </Row>
                  );
                }}
              </ProFormDependency>
              <Row>
                <Col>
                  <ProFormRadio.Group
                    name={name}
                    rules={[{ required: true }]}
                    options={[
                      {
                        label: '覆盖',
                        value: 'force',
                      },
                      {
                        label: '合并',
                        value: 'merge',
                      },
                      {
                        label: '放弃',
                        value: 'abandon',
                      },
                    ]}
                  />
                </Col>
              </Row>
            </React.Fragment>
          ))}
        </ProForm>
      )}
    </Drawer>
  );
};
