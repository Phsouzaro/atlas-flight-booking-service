import type { HttpInterceptorFn } from '@angular/common/http';

import { demoBackendInterceptor } from './demo-backend';

// Build `demo` (GitHub Pages): toda chamada a /api é respondida no navegador por um backend falso.
export const DEMO = true;
export const demoInterceptors: HttpInterceptorFn[] = [demoBackendInterceptor];
