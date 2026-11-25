package com.example.booking.config;

import com.example.booking.model.Resource;
import com.example.booking.repository.ResourceRepository;
import java.util.List;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/** При первом запуске наполняет пустую базу демонстрационными ресурсами. */
@Component
public class DataSeeder implements CommandLineRunner {

    private final ResourceRepository resources;

    public DataSeeder(ResourceRepository resources) {
        this.resources = resources;
    }

    @Override
    public void run(String... args) {
        if (resources.count() > 0) {
            return;
        }
        resources.saveAll(List.of(
                new Resource("Переговорная «Байкал»", "Экран, маркерная доска, до 6 человек", 6, 800),
                new Resource("Конференц-зал «Эльбрус»", "Проектор, звук, до 30 человек", 30, 3500),
                new Resource("Коворкинг-место №1", "Рабочее место у окна, монитор 27″", 1, 250),
                new Resource("Фотостудия", "Циклорама, комплект импульсного света", 8, 1500)));
    }
}
