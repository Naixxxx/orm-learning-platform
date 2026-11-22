package ru.study.platform.service;

import static ru.study.platform.api.dto.CatalogDtos.*;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.study.platform.domain.Category;
import ru.study.platform.domain.Tag;
import ru.study.platform.error.ApiException;
import ru.study.platform.repository.CategoryRepository;
import ru.study.platform.repository.TagRepository;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TaxonomyService {

    private final CategoryRepository categories;
    private final TagRepository tags;
    private final AccessService access;

    public List<NamedView> categories() {
        return categories.findAll(Sort.by("name")).stream()
                .map(value -> new NamedView(value.getId(), value.getName()))
                .toList();
    }

    public NamedView category(long id) {
        Category category = requireCategory(id);
        return new NamedView(id, category.getName());
    }

    @Transactional
    public NamedView saveCategory(Long id, NameInput input) {
        access.admin();
        Category category = id == null ? new Category() : requireCategory(id);
        category.setName(input.name().strip());
        categories.saveAndFlush(category);
        return new NamedView(category.getId(), category.getName());
    }

    @Transactional
    public void deleteCategory(long id) {
        access.admin();
        categories.delete(requireCategory(id));
        categories.flush();
    }

    public List<NamedView> tags() {
        return tags.findAll(Sort.by("name")).stream()
                .map(value -> new NamedView(value.getId(), value.getName()))
                .toList();
    }

    public NamedView tag(long id) {
        Tag tag = requireTag(id);
        return new NamedView(id, tag.getName());
    }

    @Transactional
    public NamedView saveTag(Long id, NameInput input) {
        access.admin();
        Tag tag = id == null ? new Tag() : requireTag(id);
        tag.setName(input.name().strip());
        tags.saveAndFlush(tag);
        return new NamedView(tag.getId(), tag.getName());
    }

    @Transactional
    public void deleteTag(long id) {
        access.admin();
        tags.delete(requireTag(id));
        tags.flush();
    }

    public Category requireCategory(long id) {
        return categories.findById(id).orElseThrow(() -> ApiException.notFound("Категория"));
    }

    private Tag requireTag(long id) {
        return tags.findById(id).orElseThrow(() -> ApiException.notFound("Тег"));
    }
}
