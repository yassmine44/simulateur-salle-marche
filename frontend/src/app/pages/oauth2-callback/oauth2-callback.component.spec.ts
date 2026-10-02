import { ComponentFixture, TestBed } from '@angular/core/testing';

import { Oauth2CallbackComponent } from './oauth2-callback.component';

describe('Oauth2CallbackComponent', () => {
  let component: Oauth2CallbackComponent;
  let fixture: ComponentFixture<Oauth2CallbackComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [Oauth2CallbackComponent]
    })
    .compileComponents();

    fixture = TestBed.createComponent(Oauth2CallbackComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
