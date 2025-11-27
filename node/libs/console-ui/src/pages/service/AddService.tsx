import { BetaSchemaForm, PageContainer } from '@ant-design/pro-components';
import {
  generateDemoService,
  ServiceConfigData,
  useCreateServiceMutation,
} from '../../apis/service';
import { App } from 'antd';
import { toInnaNameRule } from '../../common/ktor';
import {
  serviceIdColumn,
  serviceNameColumn,
  serviceTypeColumn,
} from '../../columns/service';


// https://github.com/remcohaszing/monaco-yaml/blob/main/examples/vite-example/index.js
export default () => {
  const [create] = useCreateServiceMutation();
  const { message } = App.useApp();

  return (
    <PageContainer title={'添加服务'}>
      <BetaSchemaForm
        layoutType={'Form'}
        layout={'horizontal'}
        initialValues={import.meta.env.DEV ? generateDemoService() : undefined}
        onFinish={async (input) => {
          const inputData = input as ServiceConfigData;
          await create(inputData).unwrap();
          message.success(`成功添加服务-${inputData.name}`);
          return true;
        }}
        grid
        columns={[
          serviceIdColumn,
          serviceNameColumn,
          serviceTypeColumn,
          {
            valueType: 'group',
            title: 'CPU资源',
            columns: [
              {
                dataIndex: ['resources', 'cpu', 'requestMillis'],
                valueType: 'digit',
                title: '请求值',
                width: '100%',
                colProps: {
                  xs: 12,
                  sm: 10,
                  md: 8,
                  lg: 8,
                  xl: 6,
                  xxl: 6,
                },
                tooltip: (
                  <a
                    target={'_blank'}
                    href={
                      'https://kubernetes.io/zh-cn/docs/concepts/configuration/manage-resources-containers/#meaning-of-cpu'
                    }
                  >
                    millis也可以称之为毫核
                  </a>
                ),
                fieldProps: {
                  suffix: 'Millis',
                  min: 1,
                },
                formItemProps: {
                  rules: [{ required: true }],
                },
              },
              {
                dataIndex: ['resources', 'cpu', 'limitMillis'],
                valueType: 'digit',
                title: '限制值',
                width: '100%',
                colProps: {
                  xs: 12,
                  sm: 10,
                  md: 8,
                  lg: 8,
                  xl: 6,
                  xxl: 6,
                },
                fieldProps: {
                  suffix: 'Millis',
                  min: 1,
                },
                formItemProps: {
                  rules: [{ required: true }],
                },
              },
            ],
          },
          {
            valueType: 'group',
            title: '内存资源',
            columns: [
              {
                dataIndex: ['resources', 'memory', 'requestMiB'],
                valueType: 'digit',
                title: '请求值',
                width: '100%',
                colProps: {
                  xs: 12,
                  sm: 10,
                  md: 8,
                  lg: 8,
                  xl: 6,
                  xxl: 6,
                },
                tooltip: (
                  <a
                    target={'_blank'}
                    href={
                      'https://kubernetes.io/zh-cn/docs/concepts/configuration/manage-resources-containers/#meaning-of-memory'
                    }
                  >
                    详细参考
                  </a>
                ),
                fieldProps: {
                  suffix: '兆',
                  min: 1,
                },
                formItemProps: {
                  rules: [{ required: true }],
                },
              },
              {
                dataIndex: ['resources', 'memory', 'limitMiB'],
                valueType: 'digit',
                title: '限制值',
                width: '100%',
                colProps: {
                  xs: 12,
                  sm: 10,
                  md: 8,
                  lg: 8,
                  xl: 6,
                  xxl: 6,
                },
                fieldProps: {
                  suffix: '兆',
                  min: 1,
                },
                formItemProps: {
                  rules: [{ required: true }],
                },
              },
            ],
          },
          {
            width: 'lg',
            colProps: { span: 24 },
            filled: true,
            title: '服务端口',
            dataIndex: 'ports',
            valueType: 'formList',
            columns: [
              {
                formItemProps: {
                  rules: [{ required: true }],
                },
                width: '10rem',
                // @ts-ignore
                copyable: true,
                title: '端口',
                fieldProps: {
                  min: 1,
                  max: 65536,
                },
                valueType: 'digit',
                dataIndex: 'number',
              },
              {
                formItemProps: {
                  rules: [{ required: true }, toInnaNameRule()],
                },
                width: '15rem',
                // @ts-ignore
                copyable: true,
                title: '名称',
                dataIndex: 'name',
              },
            ],
          },
        ]}
      />
      {/*<MonacoYamlEditor />*/}
      {/*<YamlEditor />*/}
      {/*<Editor*/}
      {/*  height="600px"*/}
      {/*  defaultLanguage="yaml"*/}
      {/*  // value={value}*/}
      {/*  // onChange={onChange}*/}
      {/*  // onMount={(editor, m) => {*/}
      {/*  //   configureMonacoYaml(editor, {*/}
      {/*  //     enableSchemaRequest: true,*/}
      {/*  //     isKubernetes: true,*/}
      {/*  //     schemas: [*/}
      {/*  //       {*/}
      {/*  //         uri: 'https://kubernetesjsonschema.dev/v1.18.1/all.json',*/}
      {/*  //         fileMatch: ['*'],*/}
      {/*  //       },*/}
      {/*  //     ],*/}
      {/*  //   });*/}
      {/*  // }}*/}
      {/*  // beforeMount={(monaco) => {*/}
      {/*  //   configureMonacoYaml(monaco, {*/}
      {/*  //     enableSchemaRequest: true,*/}
      {/*  //     isKubernetes: true,*/}
      {/*  //     completion: true,*/}
      {/*  //     validate: true,*/}
      {/*  //     format: true,*/}
      {/*  //     schemas: [*/}
      {/*  //       {*/}
      {/*  //         uri: 'https://kubernetesjsonschema.dev/v1.18.1/all.json',*/}
      {/*  //         fileMatch: ['*'],*/}
      {/*  //       },*/}
      {/*  //     ],*/}
      {/*  //   });*/}
      {/*  // }}*/}
      {/*/>*/}
    </PageContainer>
  );
};
