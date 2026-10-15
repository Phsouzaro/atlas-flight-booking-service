import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { App } from './app';

describe('App', () => {
  beforeEach(() => {
    sessionStorage.clear();
    TestBed.configureTestingModule({
      imports: [App],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });
  });

  it('shows only the brand while logged out', async () => {
    const fixture = TestBed.createComponent(App);
    await fixture.whenStable();
    const root = fixture.nativeElement as HTMLElement;

    expect(root.querySelector('.brand')?.textContent).toContain('Atlas');
    expect(root.querySelector('nav')).toBeNull();
  });

  it('shows navigation and the user when logged in', async () => {
    sessionStorage.setItem('atlas.session',
      JSON.stringify({ token: 't', email: 'ana@exemplo.com', expiresAt: Date.now() + 60_000 }));
    const fixture = TestBed.createComponent(App);
    await fixture.whenStable();
    const root = fixture.nativeElement as HTMLElement;

    expect(root.querySelector('nav')?.textContent).toContain('Minhas reservas');
    expect(root.textContent).toContain('ana@exemplo.com');
  });
});
