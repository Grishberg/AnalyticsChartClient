package com.grishberg.graphreporter.mvp.presenter;

import android.support.annotation.NonNull;

import com.arellomobile.mvp.InjectViewState;
import com.github.mikephil.charting.data.CandleEntry;
import com.github.mikephil.charting.data.Entry;
import com.grishberg.datafacade.ListResultCloseable;
import com.grishberg.graphreporter.data.enums.ChartMode;
import com.grishberg.graphreporter.data.enums.ChartPeriod;
import com.grishberg.graphreporter.data.model.DailyValue;
import com.grishberg.graphreporter.data.model.DualChartContainer;
import com.grishberg.graphreporter.data.model.FormulaChartContainer;
import com.grishberg.graphreporter.data.model.FormulaContainer;
import com.grishberg.graphreporter.data.repository.values.DailyDataRepository;
import com.grishberg.graphreporter.data.repository.exceptions.EmptyDataException;
import com.grishberg.graphreporter.di.DiManager;
import com.grishberg.graphreporter.mvp.common.BasePresenter;
import com.grishberg.graphreporter.mvp.view.CandlesChartView;
import com.grishberg.graphreporter.utils.LogService;
import com.grishberg.graphreporter.utils.XAxisValueToDateFormatter;
import com.grishberg.graphreporter.utils.XAxisValueToDateFormatterImpl;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

import rx.Observable;

import static com.grishberg.graphreporter.data.enums.ChartMode.CANDLE_AND_LINE_MODE;

/**
 * Created by grishberg on 01.01.17.
 * Презентер для управления экраном отображения графика с японскими свечами
 */
@InjectViewState
public class CandlesChartPresenter extends BasePresenter<CandlesChartView> implements Runnable {
    private static final int DURATION = 30 * 60 * 1000;
    private static final int FORMULA_CAPACITY = 5;
    private static final String TAG = CandlesChartPresenter.class.getSimpleName();
    private static final int INITIAL_OFFSET = 0;
    private static final boolean PAGING_ENABLED = false;
    private final List<FormulaChartContainer> formulaChartArray;
    private final List<FormulaContainer> formulaArray;
    @Inject
    LogService log;
    @Inject
    DailyDataRepository repository;
    @Inject
    ChartsHelper chartsHelper;
    private ListResultCloseable<DailyValue> dailyListResult;
    private XAxisValueToDateFormatter dateFormatter;
    private int maxOffset;
    //@Inject
    //TimerUtil timer;
    private ChartMode currentChartMode = ChartMode.CANDLE_MODE;
    private ChartPeriod period = ChartPeriod.DAY;
    private long currentProductId;
    private boolean hasMore;

    public CandlesChartPresenter() {
        DiManager.getAppComponent().inject(this);
        formulaChartArray = new ArrayList<>(FORMULA_CAPACITY);
        formulaArray = new ArrayList<>(FORMULA_CAPACITY);
        //timer.setHandler(this);
    }

    /**
     * Пересчитать точки в зависимости от периода
     *
     * @param productId -  идентификатор продукта
     */
    public void onSelectedProduct(final long productId) {
        currentProductId = productId;
        getViewState().showProgress();
        getDataFromOffset(productId, INITIAL_OFFSET, currentChartMode, period);
    }

    public void onCandlesModeClicked() {
        currentChartMode = ChartMode.CANDLE_MODE;
        onSelectedProduct(currentProductId);
    }

    public void onLinesModeClicked() {
        currentChartMode = ChartMode.LINE_MODE;
        onSelectedProduct(currentProductId);
    }

    public void onCandlesAndLinesMode() {
        currentChartMode = CANDLE_AND_LINE_MODE;
        onSelectedProduct(currentProductId);
    }

    /**
     * Смена периода
     *
     * @param period новый период
     */
    public void onPeriodChanged(final ChartPeriod period) {
        this.period = period;
        onSelectedProduct(currentProductId);
    }

    private void getDataFromOffset(final long productId,
                                   final int initialOffset,
                                   final ChartMode chartMode,
                                   final ChartPeriod period) {
        repository.getDailyValues(productId, initialOffset)
                .flatMap(dailyValues -> {
                    if (dailyValues == null || dailyValues.isEmpty()) {
                        return Observable.error(new EmptyDataException());
                    }
                    hasMore = dailyValues.size() > maxOffset;
                    maxOffset = dailyValues.size();
                    dailyListResult = dailyValues;
                    return getChartsPoints(chartMode, period, dailyValues);
                })
                .subscribe(response -> {
                    dateFormatter = new XAxisValueToDateFormatterImpl(response.getCandleResponse() != null
                            ? response.getCandleResponse().getDates()
                            : response.getEntryResponse().getDates());
                    getViewState().hideProgress();
                    getViewState().showChart(response, dateFormatter);
                    for (final FormulaChartContainer currentFormula : formulaChartArray) {
                        getViewState().formulaPoints(currentFormula);
                    }
                }, exception -> {
                    getViewState().hideProgress();
                    if (exception instanceof EmptyDataException) {
                        getViewState().showEmptyDataError();
                        return;
                    }
                    getViewState().showFail(exception.getMessage());
                    log.e(TAG, "requestDailyValues: ", exception);
                });
    }

    /**
     * перестроить формулы для нового периода
     *
     * @param period
     * @param dailyValues
     */
    private void rebuildFormulaCharts(final ChartPeriod period,
                                      final ListResultCloseable<DailyValue> dailyValues,
                                      final boolean isDualChartMode) {
        formulaChartArray.clear();
        for (final FormulaContainer formulaContainer : formulaArray) {
            formulaChartArray.add(chartsHelper.getFormulaDataForPeriod(period,
                    dailyValues,
                    formulaContainer,
                    isDualChartMode));
        }
    }

    @NonNull
    private Observable<? extends DualChartContainer> getChartsPoints(final ChartMode chartMode,
                                                                     final ChartPeriod period,
                                                                     final ListResultCloseable<DailyValue> dailyValues) {
        switch (chartMode) {
            case CANDLE_MODE:
                rebuildFormulaCharts(period, dailyValues, false);

                final DualChartContainer value = DualChartContainer.makeCandle(period,
                        chartsHelper.getCandleDataForPeriod(period, dailyValues, false));
                dateFormatter = new XAxisValueToDateFormatterImpl(
                        value.getCandleResponse() != null ? value.getCandleResponse().getDates()
                                : value.getEntryResponse().getDates());
                return Observable.just(
                        value
                );
            case LINE_MODE:
                rebuildFormulaCharts(period, dailyValues, false);
                return Observable.just(
                        DualChartContainer.makeLine(period,
                                chartsHelper.getLineData(period, dailyValues, false))
                );
            default:
                rebuildFormulaCharts(period, dailyValues, true);
                return Observable.just(
                        DualChartContainer.makeCandleAndLine(period,
                                chartsHelper.getLineData(period, dailyValues, true),
                                chartsHelper.getCandleDataForPeriod(period, dailyValues, true))
                );
        }
    }

    @Override
    public void run() {
        //TODO: update from server
    }

    /**
     * Применить формулу
     *
     * @param formulaContainer данные для построения точек формулы
     */
    public void addNewFormula(final FormulaContainer formulaContainer) {
        requestPointForFormula(currentProductId, formulaContainer, currentChartMode);
    }

    private void requestPointForFormula(final long productId,
                                        final FormulaContainer formulaContainer,
                                        final ChartMode currentChartMode) {
        formulaArray.add(formulaContainer);
        repository.getDailyValues(productId, 0)
                .flatMap(dailyValues -> {
                    if (dailyValues.isEmpty()) {
                        return Observable.error(new EmptyDataException());
                    }
                    return Observable.just(
                            chartsHelper.getFormulaDataForPeriod(period,
                                    dailyValues,
                                    formulaContainer,
                                    currentChartMode == CANDLE_AND_LINE_MODE)
                    );
                })
                .subscribe(response -> {
                    getViewState().hideProgress();
                    if (!(response.getFallPoints().isEmpty() && response.getGrowPoints().isEmpty())) {
                        formulaChartArray.add(response);
                        getViewState().formulaPoints(response);
                    }
                }, exception -> {
                    getViewState().hideProgress();
                    if (exception instanceof EmptyDataException) {
                        getViewState().showEmptyDataError();
                        return;
                    }
                    getViewState().showFail(exception.getMessage());
                    log.e(TAG, "requestDailyValues: ", exception);
                });
    }

    public void onChartValueSelected(final Entry entry) {
        if (entry instanceof CandleEntry) {
            getViewState().showPointInfo(((CandleEntry) entry).getOpen(),
                    ((CandleEntry) entry).getHigh(),
                    ((CandleEntry) entry).getLow(),
                    ((CandleEntry) entry).getClose(),
                    dateFormatter.getDateAsString(entry.getX()));
            return;
        }

        getViewState().showPointInfo(entry.getY(), dateFormatter.getDateAsString(entry.getX()));
    }

    public void onNothingSelected() {
        getViewState().hidePointInfo();
    }

    public void onScrolledToStart() {
        if (!hasMore || !PAGING_ENABLED) {
            return;
        }
        getViewState().showProgress();
        getDataFromOffset(currentProductId, dailyListResult.size(), currentChartMode, period);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (dailyListResult != null) {
            try {
                dailyListResult.close();
            } catch (final IOException e) {
                //not handled
                log.e(TAG, "onDestroy", e);
            }
        }
    }
}
