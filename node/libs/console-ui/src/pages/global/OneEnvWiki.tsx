import { useParams } from 'react-router-dom';
import { useOneWikiQuery } from '../../apis/envWiki';
import { PageContainer } from '@ant-design/pro-components';
import { Result, Skeleton } from 'antd';
import remarkGfm from 'remark-gfm';
import { MarkdownHooks } from 'react-markdown';
import { useEffect } from 'react';

export default () => {
  const { envId, title } = useParams();
  useEffect(() => {
    document.title = title ?? '';
  }, []);

  // console.debug('envId:', envId, ',title:', title);
  const { data, isLoading } = useOneWikiQuery({ env: envId!!, title: title!! });
  if (isLoading) {
    return (
      <PageContainer title={title}>
        <Skeleton />
      </PageContainer>
    );
  }
  if (!data) {
    return (
      <PageContainer title={title}>
        <Result
          status="404"
          title="404"
          subTitle="Sorry, the page you visited does not exist."
        />
      </PageContainer>
    );
  }
  return (
    <PageContainer title={title}>
      <MarkdownHooks remarkPlugins={[remarkGfm]}>{data}</MarkdownHooks>
    </PageContainer>
  );
};
