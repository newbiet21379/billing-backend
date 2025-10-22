#!/bin/bash

# Setup and Testing Script for Billing Backend Monitoring
# This script helps set up and verify the monitoring infrastructure

set -e

echo "🚀 Setting up Billing Backend Monitoring Infrastructure..."

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Function to print colored output
print_status() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

print_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

print_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

print_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Check if required tools are installed
check_dependencies() {
    print_status "Checking dependencies..."

    if ! command -v curl &> /dev/null; then
        print_error "curl is required but not installed"
        exit 1
    fi

    if ! command -v docker &> /dev/null; then
        print_warning "Docker not found - some checks may not work"
    fi

    print_success "Dependencies checked"
}

# Wait for service to be ready
wait_for_service() {
    local url=$1
    local service_name=$2
    local max_attempts=30
    local attempt=1

    print_status "Waiting for $service_name to be ready..."

    while [ $attempt -le $max_attempts ]; do
        if curl -s -f "$url" > /dev/null 2>&1; then
            print_success "$service_name is ready"
            return 0
        fi

        echo -n "."
        sleep 2
        ((attempt++))
    done

    print_error "$service_name did not become ready within ${max_attempts} attempts"
    return 1
}

# Test actuator endpoints
test_actuator_endpoints() {
    print_status "Testing Spring Boot Actuator endpoints..."

    local base_url="http://localhost:8080/actuator"

    # Test health endpoint
    print_status "Testing health endpoint..."
    if curl -s "$base_url/health" | jq '.' > /dev/null 2>&1; then
        print_success "Health endpoint is responding"

        # Test individual health components
        local components=("db" "ocr" "minio" "axon")
        for component in "${components[@]}"; do
            if curl -s "$base_url/health/$component" | jq '.' > /dev/null 2>&1; then
                print_success "Health check for $component: OK"
            else
                print_warning "Health check for $component: Not available"
            fi
        done
    else
        print_error "Health endpoint not responding"
    fi

    # Test metrics endpoint
    print_status "Testing metrics endpoint..."
    if curl -s "$base_url/metrics" | jq '.' > /dev/null 2>&1; then
        print_success "Metrics endpoint is responding"
    else
        print_error "Metrics endpoint not responding"
    fi

    # Test Prometheus endpoint
    print_status "Testing Prometheus endpoint..."
    if curl -s "$base_url/prometheus" | head -5 | grep -q "#"; then
        print_success "Prometheus endpoint is responding"
    else
        print_error "Prometheus endpoint not responding"
    fi

    # Test info endpoint
    print_status "Testing info endpoint..."
    if curl -s "$base_url/info" | jq '.' > /dev/null 2>&1; then
        print_success "Info endpoint is responding"
    else
        print_warning "Info endpoint not responding"
    fi
}

# Test API endpoints with correlation ID
test_api_with_tracing() {
    print_status "Testing API endpoints with correlation tracking..."

    # Test bill creation with correlation ID
    local correlation_id="test-$(date +%s)"
    print_status "Creating bill with correlation ID: $correlation_id"

    local response=$(curl -s -X POST \
        -H "Content-Type: application/json" \
        -H "X-Correlation-ID: $correlation_id" \
        -d '{
            "title": "Test Bill for Monitoring",
            "totalAmount": 150.75,
            "vendor": "Test Vendor",
            "dueDate": "2024-12-31T23:59:59Z"
        }' \
        http://localhost:8080/api/commands/bills)

    if echo "$response" | jq '.billId' > /dev/null 2>&1; then
        local bill_id=$(echo "$response" | jq -r '.billId')
        print_success "Bill created with ID: $bill_id"

        # Test bill approval
        print_status "Approving bill with correlation ID: $correlation_id"
        local approval_response=$(curl -s -X POST \
            -H "Content-Type: application/json" \
            -H "X-Correlation-ID: $correlation_id" \
            -d '{"approvedBy": "test-user", "approvalReason": "Monitoring test approval"}' \
            http://localhost:8080/api/commands/bills/$bill_id/approve)

        if echo "$approval_response" | jq '.billId' > /dev/null 2>&1; then
            print_success "Bill approved successfully"
        else
            print_warning "Bill approval may have failed"
        fi
    else
        print_error "Bill creation failed"
    fi
}

# Generate sample load for testing
generate_load() {
    print_status "Generating sample load for testing..."

    local duration=30  # seconds
    local requests_per_second=5
    local total_requests=$((duration * requests_per_second))

    print_status "Generating $total_requests requests over $duration seconds..."

    for i in $(seq 1 $total_requests); do
        (
            correlation_id="load-test-$(date +%s%N | cut -b1-13)-$i"
            curl -s -X POST \
                -H "Content-Type: application/json" \
                -H "X-Correlation-ID: $correlation_id" \
                -d "{
                    \"title\": \"Load Test Bill $i\",
                    \"totalAmount\": $((RANDOM % 1000 + 100)).$((RANDOM % 99)),
                    \"vendor\": \"Load Test Vendor\",
                    \"dueDate\": \"2024-12-31T23:59:59Z\"
                }" \
                http://localhost:8080/api/commands/bills > /dev/null
        ) &

        # Control request rate
        sleep $(echo "scale=3; 1/$requests_per_second" | bc -l)

        if [ $((i % 10)) -eq 0 ]; then
            echo -n "."
        fi
    done

    wait
    echo
    print_success "Load test completed"
}

# Check logs for correlation IDs
check_logs() {
    print_status "Checking logs for correlation IDs..."

    if [ -d "logs" ]; then
        local log_file=$(find logs -name "billing-backend*.json" -type f -newermt '5 minutes ago' | head -1)
        if [ -n "$log_file" ]; then
            print_status "Recent log file: $log_file"

            if grep -q "correlation_id" "$log_file"; then
                local correlation_count=$(grep -c "correlation_id" "$log_file")
                print_success "Found $correlation_count log entries with correlation IDs"

                # Show sample correlation ID entries
                print_status "Sample log entries with correlation IDs:"
                grep "correlation_id" "$log_file" | head -3 | jq '.correlation_id, .message'
            else
                print_warning "No correlation IDs found in recent logs"
            fi
        else
            print_warning "No recent log files found"
        fi
    else
        print_warning "No logs directory found"
    fi
}

# Performance benchmark
performance_benchmark() {
    print_status "Running performance benchmark..."

    # Test response times
    local iterations=10
    local total_time=0

    for i in $(seq 1 $iterations); do
        local start_time=$(date +%s%N)
        curl -s http://localhost:8080/actuator/health > /dev/null
        local end_time=$(date +%s%N)
        local duration=$(((end_time - start_time) / 1000000))
        total_time=$((total_time + duration))
        echo -n "."
    done

    local avg_time=$((total_time / iterations))
    echo
    print_success "Average health check response time: ${avg_time}ms"
}

# Generate monitoring report
generate_report() {
    print_status "Generating monitoring report..."

    local report_file="monitoring-report-$(date +%Y%m%d-%H%M%S).txt"

    cat > "$report_file" << EOF
Billing Backend Monitoring Report
Generated: $(date)

=== Service Status ===
Health Endpoint: $(curl -s http://localhost:8080/actuator/health | jq -r '.status // "ERROR"')
Metrics Available: $(curl -s http://localhost:8080/actuator/metrics | jq -r '.names | length // 0')

=== Key Metrics (Last 5 minutes) ===
Bills Created: $(curl -s "http://localhost:8080/actuator/metrics/bills.created.total" | jq -r '.measurements[0].value // 0' 2>/dev/null || echo "0")
Files Attached: $(curl -s "http://localhost:8080/actuator/metrics/files.attached.total" | jq -r '.measurements[0].value // 0' 2>/dev/null || echo "0")
OCR Processing: $(curl -s "http://localhost:8080/actuator/metrics/ocr.processing.total" | jq -r '.measurements[0].value // 0' 2>/dev/null || echo "0")

=== JVM Metrics ===
Heap Memory Used: $(curl -s "http://localhost:8080/actuator/metrics/jvm.memory.used" | jq -r '.measurements[] | select(.tags.area=="heap") | .value // 0' 2>/dev/null || echo "0") bytes
CPU Usage: $(curl -s "http://localhost:8080/actuator/metrics/process.cpu.usage" | jq -r '.measurements[0].value // 0' 2>/dev/null || echo "0")

=== Health Check Details ===
$(curl -s http://localhost:8080/actuator/health | jq '.' 2>/dev/null || echo "Health check failed")

EOF

    print_success "Monitoring report generated: $report_file"
}

# Main execution
main() {
    check_dependencies

    echo
    print_status "Starting monitoring setup verification..."
    echo

    # Wait for the application to be ready
    wait_for_service "http://localhost:8080/actuator/health" "Billing Backend"

    echo
    print_status "=== Monitoring Tests ==="
    echo

    # Test actuator endpoints
    test_actuator_endpoints
    echo

    # Test API with correlation tracking
    test_api_with_tracing
    echo

    # Check logs
    check_logs
    echo

    # Performance benchmark
    performance_benchmark
    echo

    # Optional: Generate load if requested
    if [ "$1" = "--load-test" ]; then
        generate_load
        echo
    fi

    # Generate report
    generate_report
    echo

    print_success "Monitoring setup verification completed!"
    print_status "Key endpoints to explore:"
    echo "  • Health: http://localhost:8080/actuator/health"
    echo "  • Metrics: http://localhost:8080/actuator/metrics"
    echo "  • Prometheus: http://localhost:8080/actuator/prometheus"
    echo "  • Info: http://localhost:8080/actuator/info"
    echo
    print_status "For Prometheus monitoring, see:"
    echo "  • Config: prometheus.yml"
    echo "  • Alert Rules: alert_rules.yml"
    echo "  • Grafana Dashboard: grafana-dashboard.json"
}

# Help function
show_help() {
    echo "Billing Backend Monitoring Setup Script"
    echo
    echo "Usage: $0 [OPTIONS]"
    echo
    echo "Options:"
    echo "  --load-test    Include load generation for testing"
    echo "  --help         Show this help message"
    echo
    echo "Examples:"
    echo "  $0                    # Basic monitoring verification"
    echo "  $0 --load-test        # Include load testing"
}

# Parse arguments
case "$1" in
    --help)
        show_help
        exit 0
        ;;
    --load-test)
        main --load-test
        ;;
    "")
        main
        ;;
    *)
        print_error "Unknown option: $1"
        show_help
        exit 1
        ;;
esac