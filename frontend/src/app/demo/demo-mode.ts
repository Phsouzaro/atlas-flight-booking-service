import type { HttpInterceptorFn } from '@angular/common/http';

// Build normal: sem demo. O build `demo` troca este arquivo por demo-mode.demo.ts (fileReplacements no
// angular.json), então o backend falso nem entra no bundle de produção.
export const DEMO = false;
export const demoInterceptors: HttpInterceptorFn[] = [];
