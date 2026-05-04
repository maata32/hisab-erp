import { Injectable, OnDestroy, inject } from '@angular/core';
import { BehaviorSubject, Observable, fromEvent, merge, timer } from 'rxjs';
import { switchMap, catchError, map, of, distinctUntilChanged } from 'rxjs';
import { HttpClient } from '@angular/common/http';

/**
 * Combines `navigator.onLine` events with a 30-second backend heartbeat
 * (per the spec's POS sync strategy). Emits the resulting connectivity state.
 */
@Injectable({ providedIn: 'root' })
export class OnlineStatusService implements OnDestroy {
  private readonly http = inject(HttpClient);
  private readonly subject = new BehaviorSubject<boolean>(navigator.onLine);
  readonly online$: Observable<boolean> = this.subject.asObservable();

  private heartbeatSub = merge(
    fromEvent(window, 'online').pipe(map(() => true)),
    fromEvent(window, 'offline').pipe(map(() => false)),
    timer(0, 30_000).pipe(
      switchMap(() =>
        this.http.get('/api/v1/health', { responseType: 'text' }).pipe(
          map(() => true),
          catchError(() => of(false)),
        ),
      ),
    ),
  )
    .pipe(distinctUntilChanged())
    .subscribe((v) => this.subject.next(v));

  ngOnDestroy(): void {
    this.heartbeatSub.unsubscribe();
  }

  isOnline(): boolean {
    return this.subject.value;
  }
}
