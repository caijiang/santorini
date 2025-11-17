import {render} from '@testing-library/react';

import SantoriniConsoleUi from './console-ui';

describe('SantoriniConsoleUi', () => {
  it('should render successfully', () => {
    const {baseElement} = render(<SantoriniConsoleUi/>);
    expect(baseElement).toBeTruthy();
  });
});
