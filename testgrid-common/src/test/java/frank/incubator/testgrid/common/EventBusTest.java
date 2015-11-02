package frank.incubator.testgrid.common;

import java.util.concurrent.TimeUnit;

import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;

public class EventBusTest {
	EventBus bus1;
	EventBus bus2;

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		EventBusTest eb = new EventBusTest();
		A a = new A(eb.bus1, eb.bus2);
		try {
			eb.trigger();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}

	public EventBusTest() {
		bus1 = new EventBus();
		bus2 = new EventBus();
	}

	public void trigger() throws InterruptedException {
		/*
		 * System.out.println("1:"); bus1.post( 11111 ); TimeUnit.SECONDS.sleep(
		 * 3 );
		 */
		System.out.println("2:");
		bus2.post(2222);
		TimeUnit.SECONDS.sleep(3);
		System.out.println("3:");
		bus1.post("bus1 str");
		/*
		 * TimeUnit.SECONDS.sleep( 3 ); System.out.println(":"); bus2.post(
		 * "bus2 str" );
		 */
	}

	static class A {
		public A(EventBus bus1, EventBus bus2) {
			bus2.register(this);
			bus1.register(this);

		}

		@Subscribe
		public void action1(String a) {
			System.out.println("bus1 trigger string: " + a);
		}

		@Subscribe
		public void action1(Integer a) {
			System.out.println("bus1 trigger int : " + a);
		}

		@Subscribe
		public void action2(String b) {
			System.out.println("bus2 trigger string: " + b);
		}

		@Subscribe
		public void action2(Integer b) {
			System.out.println("bus1 trigger int : " + b);
		}
	}
}
