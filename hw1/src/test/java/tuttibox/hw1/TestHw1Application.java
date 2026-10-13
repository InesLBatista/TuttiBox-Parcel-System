package tuttibox.hw1;

import org.springframework.boot.SpringApplication;

public class TestHw1Application {

	public static void main(String[] args) {
		SpringApplication.from(Hw1Application::main).with(TestcontainersConfiguration.class).run(args);
	}

}
