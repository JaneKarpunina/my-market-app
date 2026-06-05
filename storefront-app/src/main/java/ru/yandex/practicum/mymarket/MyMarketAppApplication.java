package ru.yandex.practicum.mymarket;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class MyMarketAppApplication {

	public static void main(String[] args) {

        System.out.println("ETALON_HASH: " + new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder().encode("user1"));
		SpringApplication.run(MyMarketAppApplication.class, args);
	}

}
