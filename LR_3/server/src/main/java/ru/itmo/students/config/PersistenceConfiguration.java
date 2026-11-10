package ru.itmo.students.config;

import jakarta.persistence.EntityManagerFactory;
import org.eclipse.persistence.jpa.PersistenceProvider;
import org.springframework.boot.autoconfigure.orm.jpa.JpaProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.sql.init.dependency.DependsOnDatabaseInitialization;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.EclipseLinkJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;

@Configuration
@EnableTransactionManagement
@EnableJpaRepositories(basePackages = "ru.itmo.students.repository")
@EnableConfigurationProperties(JpaProperties.class)
public class PersistenceConfiguration {
    @Bean
    @DependsOnDatabaseInitialization
    public LocalContainerEntityManagerFactoryBean entityManagerFactory(
            DataSource dataSource, JpaProperties properties) {
        var factory = new LocalContainerEntityManagerFactoryBean();
        factory.setDataSource(dataSource);
        factory.setPackagesToScan("ru.itmo.students.entity");
        factory.setPersistenceUnitName("students");
        factory.setPersistenceProvider(new PersistenceProvider());
        factory.setJpaVendorAdapter(new EclipseLinkJpaVendorAdapter());
        factory.setJpaPropertyMap(properties.getProperties());
        return factory;
    }

    @Bean
    public PlatformTransactionManager transactionManager(EntityManagerFactory factory) {
        return new JpaTransactionManager(factory);
    }
}
