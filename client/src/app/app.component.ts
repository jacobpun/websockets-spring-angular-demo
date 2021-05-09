import { Component, OnInit } from '@angular/core';

@Component({
  selector: 'app-root',
  templateUrl: './app.component.html',
  styleUrls: ['./app.component.css']
})
export class AppComponent {
  events: Event[] = [];
  private ws: WebSocket = new WebSocket('ws://localhost:8080/ws/events');

  constructor() {
    this.ws.onmessage = (event: MessageEvent) => {
      this.events = [
        JSON.parse(event.data) as Event,
        ...this.events
      ]
    }
  }

}


interface Event {
  eventDate: Date,
  message: string
}