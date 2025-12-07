import { describe } from 'vitest';
import { encryptString } from './DashboardLink';

describe('DashboardLink', () => {
  it('go3', async () => {
    const ded = await encryptString('hello');
    console.log(ded);
  });
});
