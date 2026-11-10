package ru.itmo.students;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;

@SpringBootApplication(exclude = {
        HibernateJpaAutoConfiguration.class,
        UserDetailsServiceAutoConfiguration.class
})
public class StudentsApplication {
    public static void main(String[] args) {
        SpringApplication.run(StudentsApplication.class, args);
    }
}
