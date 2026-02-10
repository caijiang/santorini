import { PageContainer, ProCard } from '@ant-design/pro-components';
import demoObj from './preview.json';
import { SSADrawer } from './ServerSideApplyDrawer';

export default () => {
  return (
    <PageContainer title={'预览ServerSideApplyDrawer'}>
      <ProCard>空白 Card</ProCard>
      <SSADrawer state={demoObj} />
    </PageContainer>
  );
};
