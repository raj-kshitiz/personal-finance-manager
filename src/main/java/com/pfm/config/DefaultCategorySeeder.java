package com.pfm.config;

import com.pfm.entity.Category;
import com.pfm.entity.CategoryType;
import com.pfm.repository.CategoryRepository;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.ApplicationArguments;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.annotation.Transactional;

/**
 * Seeds the system default categories on startup. The seeder is idempotent: it only inserts the
 * categories that are missing, so it is safe to run against a persistent database.
 */
@Configuration
public class DefaultCategorySeeder {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultCategorySeeder.class);

    /** The default categories required by the assignment, in display order. */
    public static final Map<String, CategoryType> DEFAULT_CATEGORIES;

    static {
        Map<String, CategoryType> defaults = new LinkedHashMap<>();
        defaults.put("Salary", CategoryType.INCOME);
        defaults.put("Food", CategoryType.EXPENSE);
        defaults.put("Rent", CategoryType.EXPENSE);
        defaults.put("Transportation", CategoryType.EXPENSE);
        defaults.put("Entertainment", CategoryType.EXPENSE);
        defaults.put("Healthcare", CategoryType.EXPENSE);
        defaults.put("Utilities", CategoryType.EXPENSE);
        DEFAULT_CATEGORIES = Map.copyOf(defaults);
    }

    /**
     * Creates the startup runner that seeds missing default categories.
     *
     * @param repository the category repository
     * @return the application runner
     */
    @Bean
    public ApplicationRunner seedDefaultCategories(CategoryRepository repository) {
        return new ApplicationRunner() {
            @Override
            @Transactional
            public void run(ApplicationArguments args) {
                DEFAULT_CATEGORIES.forEach((name, type) -> {
                    if (repository.findDefaultByName(name).isEmpty()) {
                        repository.save(Category.defaultCategory(name, type));
                        LOG.info("Seeded default category {} ({})", name, type);
                    }
                });
            }
        };
    }
}
