package com.grishberg.graphreporter.data.rest;

import com.grishberg.graphreporter.data.model.AuthContainer;
import com.grishberg.graphreporter.data.model.DailyValue;
import com.grishberg.graphreporter.data.model.ProductItem;
import com.grishberg.graphreporter.data.model.RefreshTokenContainer;
import com.grishberg.graphreporter.data.model.common.RestResponse;

import java.util.List;

import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Query;
import rx.Observable;

/**
 * Created by grishberg on 12.01.17.
 */

public interface Api {
    @POST("auth")
    Observable<RestResponse<AuthContainer>> login(@Query("login") String login,
                                                  @Query("password") CharSequence pass);

    @POST("refreshToken")
    Observable<RestResponse<RefreshTokenContainer>> refreshToken(@Query("refreshToken") String refreshToken);

    //data

    @GET("dailyValues")
    Observable<RestResponse<List<DailyValue>>> getDailyData(@Query("accessToken") String accessToken,
                                                            @Query("productId") long productId,
                                                            @Query("offset") long offset,
                                                            @Query("limit") long limit);

    @GET("products")
    Observable<RestResponse<List<ProductItem>>> getProducts(@Query("accessToken") String accessToken,
                                                            @Query("categoryId") long categoryId);
}
