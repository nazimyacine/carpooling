import { Component, inject } from '@angular/core';
import { ActivatedRoute } from '@angular/router';

@Component({
  selector: 'app-home',
  template: `<section class="welcome"><p class="eyebrow">{{ area }}</p><h1>Votre espace est prêt.</h1><p>{{ description }}</p></section>`
})
export class Home {
  private readonly route = inject(ActivatedRoute);
  readonly area = this.route.snapshot.data['area'] as string;
  readonly description = this.route.snapshot.data['description'] as string;
}
