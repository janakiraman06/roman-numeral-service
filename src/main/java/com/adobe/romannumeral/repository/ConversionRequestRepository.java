package com.adobe.romannumeral.repository;

import com.adobe.romannumeral.entity.ConversionRequest;
import com.adobe.romannumeral.entity.User;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Repository for ConversionRequest entity operations.
 *
 * <p>This is the primary analytics repository, providing methods for
 * querying conversion request history and generating usage statistics.</p>
 *
 * <p>Design Note: For high-volume production use, consider:
 * <ul>
 *   <li>Partitioning by requestTimestamp for time-series performance</li>
 *   <li>Archiving old records to the data lake (Iceberg tables)</li>
 *   <li>Using read replicas for analytics queries</li>
 * </ul>
 * </p>
 */
@Repository
public interface ConversionRequestRepository extends JpaRepository<ConversionRequest, Long> {

    /**
     * Finds all requests for a specific user with pagination.
     *
     * @param user the user
     * @param pageable pagination parameters
     * @return page of conversion requests
     */
    Page<ConversionRequest> findByUser(User user, Pageable pageable);

    /**
     * Finds requests within a time range.
     * Useful for time-series analysis and data lake sync.
     *
     * @param start start of time range
     * @param end end of time range
     * @return list of requests in the time range
     */
    List<ConversionRequest> findByRequestTimestampBetween(Instant start, Instant end);

    /**
     * Finds requests by type within a time range.
     *
     * @param type the request type (SINGLE or RANGE)
     * @param start start of time range
     * @param end end of time range
     * @return list of matching requests
     */
    @Query("SELECT cr FROM ConversionRequest cr WHERE cr.requestType = :type "
            + "AND cr.requestTimestamp BETWEEN :start AND :end")
    List<ConversionRequest> findByTypeAndTimeRange(
            @Param("type") ConversionRequest.RequestType type,
            @Param("start") Instant start,
            @Param("end") Instant end);

    /**
     * Counts requests by type.
     *
     * @param type the request type
     * @return count of requests
     */
    long countByRequestType(ConversionRequest.RequestType type);

    /**
     * Gets the most frequently requested single numbers.
     * Useful for caching optimization and usage analytics.
     *
     * @param pageable limit the results
     * @return list of [inputNumber, count] pairs
     */
    @Query("SELECT cr.inputNumber, COUNT(cr) as cnt FROM ConversionRequest cr "
            + "WHERE cr.requestType = 'SINGLE' AND cr.inputNumber IS NOT NULL "
            + "GROUP BY cr.inputNumber ORDER BY cnt DESC")
    List<Object[]> findMostRequestedNumbers(Pageable pageable);

    /**
     * Calculates average response time in milliseconds for a time period.
     *
     * @param start start of time range
     * @param end end of time range
     * @return average response time in nanoseconds
     */
    @Query("SELECT AVG(cr.responseTimeNanos) FROM ConversionRequest cr "
            + "WHERE cr.requestTimestamp BETWEEN :start AND :end")
    Double findAverageResponseTime(@Param("start") Instant start, @Param("end") Instant end);

    /**
     * Gets request counts grouped by hour.
     * Useful for traffic pattern analysis.
     *
     * @param start start of time range
     * @param end end of time range
     * @return list of [hour, count] pairs
     */
    @Query(value = "SELECT DATE_TRUNC('hour', request_timestamp) as hour, COUNT(*) "
            + "FROM conversion_requests "
            + "WHERE request_timestamp BETWEEN :start AND :end "
            + "GROUP BY DATE_TRUNC('hour', request_timestamp) "
            + "ORDER BY hour", nativeQuery = true)
    List<Object[]> findRequestCountsByHour(
            @Param("start") Instant start,
            @Param("end") Instant end);

    /**
     * Finds late-arriving data (processing time significantly after event time).
     * Useful for data quality monitoring.
     *
     * @param delayThresholdSeconds threshold in seconds
     * @return list of late requests
     */
    @Query("SELECT cr FROM ConversionRequest cr "
            + "WHERE FUNCTION('TIMESTAMPDIFF', SECOND, cr.requestTimestamp, cr.createdAt) > :threshold")
    List<ConversionRequest> findLateArrivingData(@Param("threshold") long delayThresholdSeconds);
}

