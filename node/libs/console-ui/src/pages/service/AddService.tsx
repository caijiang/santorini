import {BetaSchemaForm, PageContainer} from '@ant-design/pro-components';
import {io} from '@santorini/generated-santorini-model';
import _ from 'lodash';
import {ProSchemaValueEnumType} from '@ant-design/pro-provider';

/**
 * 将 kotlin 原生枚举处理成  ProSchemaValueEnumType
 * @param input 枚举数组
 * @param toEnum 可选的额外处理函数
 */
function toProSchemaValueEnumMap<
  T extends {
    name: string;
  }
>(
  input: Array<T>,
  toEnum: (input: T) => ProSchemaValueEnumType | undefined = (it) => ({
    text: it.name,
  })
) {
  return _.transform(
    input,
    (obj, it) => {
      const x = toEnum(it);
      if (x) {
        obj[it.name] = x;
      }
    },
    {} as Record<string, ProSchemaValueEnumType>
  );
}

function toInnaNameRule(number: number = 15) {
  return {
    pattern: RegExp(`^[a-z]([a-z0-9-]{0,${number - 1}})$`),
    message: `非法 IANA Name（必须是 1–${number} 位小写字母、数字或 -，且字母开头）`,
  };
}

// https://github.com/remcohaszing/monaco-yaml/blob/main/examples/vite-example/index.js
export default () => {
  return (
    <PageContainer title={'添加服务'}>
      <BetaSchemaForm
        layoutType={'Form'}
        layout={'horizontal'}
        onFinish={async (input) => {
          console.log(input);
        }}
        grid
        columns={[
          {
            dataIndex: 'id',
            title: '编码',
            colProps: {
              xs: 24,
              sm: 24,
              md: 12,
              lg: 10,
              xl: 5,
              xxl: 4,
            },
            formItemProps: {
              rules: [
                { required: true },
                { min: 3 },
                toInnaNameRule(63 - '.deployment'.length),
              ],
            },
          },
          {
            dataIndex: 'name',
            title: '名称',
            colProps: {
              xs: 24,
              sm: 24,
              md: 12,
              lg: 12,
              xl: 8,
              xxl: 6,
            },
            formItemProps: {
              rules: [{ required: true }, { max: 50 }],
            },
          },
          {
            dataIndex: 'type',
            title: '类型',
            colProps: {
              xs: 12,
              sm: 12,
              md: 6,
              lg: 6,
              xl: 6,
              xxl: 4,
            },
            formItemProps: {
              rules: [{ required: true }],
            },
            valueEnum: toProSchemaValueEnumMap(
              io.santorini.model.ServiceType.values()
            ),
          },
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
