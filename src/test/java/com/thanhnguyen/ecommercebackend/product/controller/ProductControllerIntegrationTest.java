package com.thanhnguyen.ecommercebackend.product.controller;

import com.thanhnguyen.ecommercebackend.TestcontainersConfiguration;
import com.thanhnguyen.ecommercebackend.cart.dto.AddCartItemRequest;
import com.thanhnguyen.ecommercebackend.inventory.dto.UpdateInventoryRequest;
import com.thanhnguyen.ecommercebackend.order.dto.CheckoutRequest;
import com.thanhnguyen.ecommercebackend.product.dto.CategoryRequest;
import com.thanhnguyen.ecommercebackend.product.dto.ProductCreateRequest;
import com.thanhnguyen.ecommercebackend.product.dto.ProductImageRequest;
import com.thanhnguyen.ecommercebackend.product.dto.ProductStatusUpdateRequest;
import com.thanhnguyen.ecommercebackend.product.dto.ProductUpdateRequest;
import com.thanhnguyen.ecommercebackend.product.entity.ProductStatus;
import com.thanhnguyen.ecommercebackend.user.dto.BecomeSellerRequest;
import com.thanhnguyen.ecommercebackend.user.dto.LoginRequest;
import com.thanhnguyen.ecommercebackend.user.dto.RegisterRequest;
import com.thanhnguyen.ecommercebackend.user.entity.User;
import com.thanhnguyen.ecommercebackend.user.entity.UserRole;
import com.thanhnguyen.ecommercebackend.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.List;

import static org.hamcrest.Matchers.is;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class ProductControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    private String registerAndLogin(String email, UserRole role) throws Exception {
        RegisterRequest registerRequest = new RegisterRequest();
        registerRequest.setEmail(email);
        registerRequest.setPassword("Password123");
        registerRequest.setFullName("Product Test User");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isCreated());

        if (role != UserRole.CUSTOMER) {
            User user = userRepository.findByEmail(email).orElseThrow();
            user.setRole(role);
            userRepository.save(user);
        }

        LoginRequest loginRequest = new LoginRequest(email, "Password123");
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString()).get("data");
        return data.get("accessToken").asText();
    }

    private String registerSeller(String email) throws Exception {
        String accessToken = registerAndLogin(email, UserRole.CUSTOMER);

        mockMvc.perform(post("/api/sellers")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new BecomeSellerRequest("Shop of " + email, null))))
                .andExpect(status().isCreated());

        return accessToken;
    }

    private Long createCategory(String adminToken, String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/admin/categories")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CategoryRequest(name, null))))
                .andExpect(status().isCreated())
                .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsString()).get("data").get("id").asLong();
    }

    @Test
    void create_shouldReturn201_whenSellerCreatesProduct() throws Exception {
        String adminToken = registerAndLogin("prod-admin1@example.com", UserRole.ADMIN);
        Long categoryId = createCategory(adminToken, "Electronics-1");
        String sellerToken = registerSeller("prod-seller1@example.com");

        ProductCreateRequest request = new ProductCreateRequest(
                "Laptop", "A laptop", new BigDecimal("999.99"), categoryId, null,
                List.of(new ProductImageRequest("http://img/1.png", false),
                        new ProductImageRequest("http://img/2.png", false)));

        mockMvc.perform(post("/api/seller/products")
                        .header("Authorization", "Bearer " + sellerToken)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.name", is("Laptop")))
                .andExpect(jsonPath("$.data.status", is("ACTIVE")))
                .andExpect(jsonPath("$.data.images[0].primary", is(true)));
    }

    @Test
    void create_shouldReturn403_whenUserIsNotSeller() throws Exception {
        String adminToken = registerAndLogin("prod-admin2@example.com", UserRole.ADMIN);
        Long categoryId = createCategory(adminToken, "Electronics-2");
        String customerToken = registerAndLogin("prod-customer1@example.com", UserRole.CUSTOMER);

        ProductCreateRequest request = new ProductCreateRequest(
                "Laptop", "A laptop", new BigDecimal("999.99"), categoryId, null, null);

        mockMvc.perform(post("/api/seller/products")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code", is("NOT_A_SELLER")));
    }

    @Test
    void create_shouldReturn404_whenCategoryDoesNotExist() throws Exception {
        String sellerToken = registerSeller("prod-seller2@example.com");

        ProductCreateRequest request = new ProductCreateRequest(
                "Laptop", "A laptop", new BigDecimal("999.99"), 999999L, null, null);

        mockMvc.perform(post("/api/seller/products")
                        .header("Authorization", "Bearer " + sellerToken)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code", is("CATEGORY_NOT_FOUND")));
    }

    @Test
    void update_shouldReturn403_whenNotOwner() throws Exception {
        String adminToken = registerAndLogin("prod-admin3@example.com", UserRole.ADMIN);
        Long categoryId = createCategory(adminToken, "Electronics-3");
        String ownerToken = registerSeller("prod-seller3@example.com");
        String otherSellerToken = registerSeller("prod-seller4@example.com");

        MvcResult createResult = mockMvc.perform(post("/api/seller/products")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ProductCreateRequest(
                                "Phone", "A phone", new BigDecimal("499.99"), categoryId, null, null))))
                .andExpect(status().isCreated())
                .andReturn();

        Long productId = objectMapper.readTree(createResult.getResponse().getContentAsString())
                .get("data").get("id").asLong();

        ProductUpdateRequest updateRequest = new ProductUpdateRequest(
                "Hacked name", null, null, null, null, null);

        mockMvc.perform(patch("/api/seller/products/" + productId)
                        .header("Authorization", "Bearer " + otherSellerToken)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code", is("PRODUCT_OWNERSHIP_VIOLATION")));
    }

    @Test
    void publicEndpoints_shouldHideInactiveProduct() throws Exception {
        String adminToken = registerAndLogin("prod-admin4@example.com", UserRole.ADMIN);
        Long categoryId = createCategory(adminToken, "Electronics-4");
        String sellerToken = registerSeller("prod-seller5@example.com");

        MvcResult createResult = mockMvc.perform(post("/api/seller/products")
                        .header("Authorization", "Bearer " + sellerToken)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ProductCreateRequest(
                                "Tablet", "A tablet", new BigDecimal("299.99"), categoryId, null, null))))
                .andExpect(status().isCreated())
                .andReturn();

        Long productId = objectMapper.readTree(createResult.getResponse().getContentAsString())
                .get("data").get("id").asLong();

        mockMvc.perform(get("/api/products/" + productId))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/api/seller/products/" + productId + "/status")
                        .header("Authorization", "Bearer " + sellerToken)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ProductStatusUpdateRequest(ProductStatus.INACTIVE))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("INACTIVE")));

        mockMvc.perform(get("/api/products/" + productId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code", is("PRODUCT_NOT_FOUND")));
    }

    @Test
    void create_shouldReturn400_whenMultipleImagesMarkedPrimary() throws Exception {
        String adminToken = registerAndLogin("prod-admin5@example.com", UserRole.ADMIN);
        Long categoryId = createCategory(adminToken, "Electronics-5");
        String sellerToken = registerSeller("prod-seller6@example.com");

        ProductCreateRequest request = new ProductCreateRequest(
                "Camera", "A camera", new BigDecimal("199.99"), categoryId, null,
                List.of(new ProductImageRequest("http://img/1.png", true),
                        new ProductImageRequest("http://img/2.png", true)));

        mockMvc.perform(post("/api/seller/products")
                        .header("Authorization", "Bearer " + sellerToken)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code", is("MULTIPLE_PRIMARY_IMAGES")));
    }

    @Test
    void listActive_shouldBePublic_noAuthNeeded() throws Exception {
        mockMvc.perform(get("/api/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)));
    }

    @Test
    void search_shouldFilterByCategoryPriceKeywordAndMinRating() throws Exception {
        String adminToken = registerAndLogin("prod-admin-search1@example.com", UserRole.ADMIN);
        Long categoryA = createCategory(adminToken, "Search-CategoryA");
        Long categoryB = createCategory(adminToken, "Search-CategoryB");
        String sellerToken = registerSeller("prod-seller-search1@example.com");

        createProduct(sellerToken, "Gaming Laptop", categoryA, new BigDecimal("1500.00"));
        createProduct(sellerToken, "Office Laptop", categoryA, new BigDecimal("500.00"));
        createProduct(sellerToken, "Gaming Mouse", categoryB, new BigDecimal("50.00"));

        mockMvc.perform(get("/api/products")
                        .param("category", categoryA.toString())
                        .param("keyword", "laptop")
                        .param("minPrice", "1000")
                        .param("maxPrice", "2000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()", is(1)))
                .andExpect(jsonPath("$.data.content[0].name", is("Gaming Laptop")))
                .andExpect(jsonPath("$.data.totalElements", is(1)));
    }

    @Test
    void search_shouldReturnOnlyInStockProducts_whenInStockOnlyTrue() throws Exception {
        String adminToken = registerAndLogin("prod-admin-search2@example.com", UserRole.ADMIN);
        Long categoryId = createCategory(adminToken, "Search-CategoryC");
        String sellerToken = registerSeller("prod-seller-search2@example.com");

        Long outOfStockId = createProduct(sellerToken, "Out Of Stock Item", categoryId, new BigDecimal("10.00"));
        Long inStockId = createProduct(sellerToken, "In Stock Item", categoryId, new BigDecimal("10.00"));

        mockMvc.perform(patch("/api/seller/products/" + inStockId + "/inventory")
                        .header("Authorization", "Bearer " + sellerToken)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateInventoryRequest(5))))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/products").param("category", categoryId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()", is(2)));

        mockMvc.perform(get("/api/products")
                        .param("category", categoryId.toString())
                        .param("inStockOnly", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()", is(1)))
                .andExpect(jsonPath("$.data.content[0].id", is(inStockId.intValue())));
    }

    @Test
    void search_shouldPaginate() throws Exception {
        String adminToken = registerAndLogin("prod-admin-search3@example.com", UserRole.ADMIN);
        Long categoryId = createCategory(adminToken, "Search-CategoryD");
        String sellerToken = registerSeller("prod-seller-search3@example.com");

        createProduct(sellerToken, "Paginated Item 1", categoryId, new BigDecimal("10.00"));
        createProduct(sellerToken, "Paginated Item 2", categoryId, new BigDecimal("10.00"));
        createProduct(sellerToken, "Paginated Item 3", categoryId, new BigDecimal("10.00"));

        mockMvc.perform(get("/api/products")
                        .param("category", categoryId.toString())
                        .param("page", "0")
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()", is(2)))
                .andExpect(jsonPath("$.data.totalElements", is(3)))
                .andExpect(jsonPath("$.data.totalPages", is(2)));

        mockMvc.perform(get("/api/products")
                        .param("category", categoryId.toString())
                        .param("page", "1")
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()", is(1)));
    }

    @Test
    void search_shouldReturn400_whenSortFieldIsInvalid() throws Exception {
        mockMvc.perform(get("/api/products").param("sort", "doesNotExist,asc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code", is("INVALID_SORT_FIELD")));
    }

    @Test
    void search_inStockOnly_shouldReflectReserveAndReleaseStock_viaCheckoutAndCancel() throws Exception {
        String adminToken = registerAndLogin("prod-admin-search4@example.com", UserRole.ADMIN);
        Long categoryId = createCategory(adminToken, "Search-CategoryE");
        String sellerToken = registerSeller("prod-seller-search4@example.com");
        String customerToken = registerAndLogin("prod-customer-search4@example.com", UserRole.CUSTOMER);

        Long productId = createProduct(sellerToken, "Reservable Item", categoryId, new BigDecimal("20.00"));
        mockMvc.perform(patch("/api/seller/products/" + productId + "/inventory")
                        .header("Authorization", "Bearer " + sellerToken)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateInventoryRequest(1))))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/products").param("category", categoryId.toString()).param("inStockOnly", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()", is(1)));

        // Checkout het 1 don vi cuoi -> InventoryService.reserveStock -> available 1->0 -> inStock phai tu dong ve false
        mockMvc.perform(post("/api/cart/items")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AddCartItemRequest(productId, 1))))
                .andExpect(status().isCreated());
        MvcResult checkoutResult = mockMvc.perform(post("/api/orders/checkout")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CheckoutRequest("Nguyen Van A", "0900000000", "123 Test Street", null))))
                .andExpect(status().isCreated())
                .andReturn();
        Long orderId = objectMapper.readTree(checkoutResult.getResponse().getContentAsString())
                .get("data").get("id").asLong();

        mockMvc.perform(get("/api/products").param("category", categoryId.toString()).param("inStockOnly", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()", is(0)));

        // Customer huy don (PENDING_PAYMENT) -> InventoryService.releaseStock -> available 0->1 -> inStock ve lai true
        mockMvc.perform(post("/api/orders/" + orderId + "/cancel")
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/products").param("category", categoryId.toString()).param("inStockOnly", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()", is(1)));
    }

    private Long createProduct(String sellerToken, String name, Long categoryId, BigDecimal price) throws Exception {
        ProductCreateRequest request = new ProductCreateRequest(name, "desc", price, categoryId, null, null);

        MvcResult result = mockMvc.perform(post("/api/seller/products")
                        .header("Authorization", "Bearer " + sellerToken)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsString()).get("data").get("id").asLong();
    }
}
