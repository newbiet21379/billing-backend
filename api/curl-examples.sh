#!/bin/bash

# Billing & Expense Processing Service - cURL Examples
# This script provides comprehensive API examples for testing and integration

# Configuration
BASE_URL="${BASE_URL:-http://localhost:8080}"
API_KEY="${API_KEY:-dev-api-key}"
OUTPUT_DIR="${OUTPUT_DIR:-./api/sample-responses}"

# Create output directory for sample responses
mkdir -p "$OUTPUT_DIR"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Utility functions
log_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

log_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

log_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Common headers
COMMON_HEADERS=(
    -H "Content-Type: application/json"
    -H "X-API-Key: $API_KEY"
    -H "Accept: application/json"
)

# Function to make API call and save response
make_api_call() {
    local method=$1
    local endpoint=$2
    local data=$3
    local output_file=$4
    local headers=("${@:5}")

    local url="$BASE_URL$endpoint"

    log_info "Making $method request to: $url"

    if [ -n "$data" ]; then
        response=$(curl -s -w "\nHTTP_STATUS:%{http_code}" \
            -X "$method" \
            "${COMMON_HEADERS[@]}" \
            "${headers[@]}" \
            -d "$data" \
            "$url")
    else
        response=$(curl -s -w "\nHTTP_STATUS:%{http_code}" \
            -X "$method" \
            "${COMMON_HEADERS[@]}" \
            "${headers[@]}" \
            "$url")
    fi

    # Split response and status code
    http_status=$(echo "$response" | grep "HTTP_STATUS:" | cut -d: -f2)
    body=$(echo "$response" | sed -e 's/HTTP_STATUS:.*//g')

    # Save response to file
    if [ -n "$output_file" ]; then
        echo "$body" | jq '.' > "$OUTPUT_DIR/$output_file" 2>/dev/null || echo "$body" > "$OUTPUT_DIR/$output_file"
        log_info "Response saved to: $OUTPUT_DIR/$output_file"
    fi

    # Check status
    if [ "$http_status" -ge 200 ] && [ "$http_status" -lt 300 ]; then
        log_success "Request successful (HTTP $http_status)"
    else
        log_error "Request failed (HTTP $http_status)"
    fi

    echo "$body"

    # Return HTTP status as exit code
    return "$http_status"
}

# Extract values from JSON responses
extract_json_value() {
    local json=$1
    local path=$2

    echo "$json" | jq -r "$path" 2>/dev/null || echo ""
}

# ================
# BILL COMMANDS
# ================

# Create a new bill
create_bill() {
    log_info "Creating a new bill..."

    bill_data='{
        "title": "Office Supplies Invoice",
        "total": 150.75,
        "vendor": "Staples",
        "dueDate": "2024-12-15",
        "category": "OFFICE_SUPPLIES",
        "description": "Monthly office supplies restocking"
    }'

    response=$(make_api_call "POST" "/api/commands/bills" "$bill_data" "create-bill-response.json")

    # Extract and store bill ID for subsequent requests
    bill_id=$(extract_json_value "$response" '.data.billId')
    if [ "$bill_id" != "null" ] && [ -n "$bill_id" ]; then
        echo "BILL_ID=$bill_id"
        log_success "Bill created with ID: $bill_id"
    else
        log_error "Failed to extract bill ID from response"
    fi

    echo "$response"
}

# Update a bill
update_bill() {
    local bill_id=$1

    if [ -z "$bill_id" ]; then
        log_error "Bill ID is required"
        return 1
    fi

    log_info "Updating bill: $bill_id"

    update_data='{
        "title": "Updated Office Supplies Invoice",
        "total": 175.50,
        "vendor": "Office Depot",
        "dueDate": "2024-12-20",
        "category": "OFFICE_SUPPLIES",
        "description": "Updated monthly office supplies restocking"
    }'

    make_api_call "PUT" "/api/commands/bills/$bill_id" "$update_data" "update-bill-response.json"
}

# Attach file to bill
attach_file() {
    local bill_id=$1
    local file_path=$2

    if [ -z "$bill_id" ]; then
        log_error "Bill ID is required"
        return 1
    fi

    if [ -z "$file_path" ] || [ ! -f "$file_path" ]; then
        log_error "Valid file path is required"
        return 1
    fi

    log_info "Attaching file to bill: $bill_id"
    log_info "File: $file_path"

    # Generate file name
    file_name=$(basename "$file_path")
    content_type=$(file -b --mime-type "$file_path")
    file_size=$(stat -f%z "$file_path" 2>/dev/null || stat -c%s "$file_path" 2>/dev/null)

    # Get upload URL first
    upload_data="{
        \"fileName\": \"$file_name\",
        \"contentType\": \"$content_type\",
        \"fileSize\": $file_size,
        \"bucket\": \"bills\",
        \"expirationMinutes\": 15
    }"

    upload_response=$(make_api_call "POST" "/api/storage/upload-url" "$upload_data" "upload-url-response.json")
    upload_url=$(extract_json_value "$upload_response" '.data.uploadUrl')
    file_id=$(extract_json_value "$upload_response" '.data.fileId')

    if [ "$upload_url" != "null" ] && [ -n "$upload_url" ]; then
        # Upload file to MinIO
        log_info "Uploading file to storage..."

        curl -s -X PUT \
            -H "Content-Type: $content_type" \
            -T "$file_path" \
            "$upload_url"

        if [ $? -eq 0 ]; then
            log_success "File uploaded successfully"

            # Trigger file attachment in backend
            attach_data="{
                \"fileId\": \"$file_id\",
                \"fileName\": \"$file_name\",
                \"fileSize\": $file_size,
                \"contentType\": \"$content_type\"
            }"

            make_api_call "POST" "/api/commands/bills/$bill_id/file" "$attach_data" "attach-file-response.json"

            echo "FILE_ID=$file_id"
        else
            log_error "Failed to upload file to storage"
        fi
    else
        log_error "Failed to get upload URL"
    fi
}

# Approve a bill
approve_bill() {
    local bill_id=$1

    if [ -z "$bill_id" ]; then
        log_error "Bill ID is required"
        return 1
    fi

    log_info "Approving bill: $bill_id"

    approve_data='{
        "approvedBy": "john.doe@company.com",
        "comments": "Invoice verified and approved for payment",
        "approvedAt": "'$(date -u +"%Y-%m-%dT%H:%M:%SZ")'"
    }'

    make_api_call "POST" "/api/commands/bills/$bill_id/approve" "$approve_data" "approve-bill-response.json"
}

# Reject a bill
reject_bill() {
    local bill_id=$1

    if [ -z "$bill_id" ]; then
        log_error "Bill ID is required"
        return 1
    fi

    log_info "Rejecting bill: $bill_id"

    reject_data='{
        "rejectedBy": "jane.smith@company.com",
        "reason": "Invalid vendor information",
        "comments": "Vendor address does not match records",
        "rejectedAt": "'$(date -u +"%Y-%m-%dT%H:%M:%SZ")'"
    }'

    make_api_call "POST" "/api/commands/bills/$bill_id/reject" "$reject_data" "reject-bill-response.json"
}

# Delete a bill
delete_bill() {
    local bill_id=$1

    if [ -z "$bill_id" ]; then
        log_error "Bill ID is required"
        return 1
    fi

    log_info "Deleting bill: $bill_id"

    make_api_call "DELETE" "/api/commands/bills/$bill_id" "" "delete-bill-response.json"
}

# ================
# BILL QUERIES
# ================

# List bills
list_bills() {
    local page=${1:-0}
    local size=${2:-20}
    local sort=${3:-"createdAt,desc"}
    local status=${4:-""}
    local vendor=${5:-""}

    log_info "Listing bills (page=$page, size=$size, sort=$sort)"

    # Build query parameters
    params="page=$page&size=$size&sort=$sort"

    if [ -n "$status" ]; then
        params="$params&status=$status"
    fi

    if [ -n "$vendor" ]; then
        params="$params&vendor=$vendor"
    fi

    make_api_call "GET" "/api/queries/bills?$params" "" "list-bills-response.json"
}

# Get bill details
get_bill_details() {
    local bill_id=$1

    if [ -z "$bill_id" ]; then
        log_error "Bill ID is required"
        return 1
    fi

    log_info "Getting bill details: $bill_id"

    make_api_call "GET" "/api/queries/bills/$bill_id" "" "bill-details-response.json"
}

# Search bills
search_bills() {
    local search_title=${1:-"office"}
    local min_total=${2:-100}
    local max_total=${3:-500}

    log_info "Searching bills (title contains: $search_title, total: $min_total-$max_total)"

    search_data="{
        \"filters\": {
            \"title\": {
                \"contains\": \"$search_title\",
                \"caseSensitive\": false
            },
            \"total\": {
                \"min\": $min_total,
                \"max\": $max_total
            },
            \"vendor\": {
                \"in\": [\"Staples\", \"Office Depot\", \"Amazon\"]
            },
            \"status\": [\"PENDING\", \"COMPLETED\"],
            \"createdDate\": {
                \"from\": \"2024-01-01T00:00:00Z\",
                \"to\": \"2024-12-31T23:59:59Z\"
            }
        },
        \"pagination\": {
            \"page\": 0,
            \"size\": 20,
            \"sort\": [
                {
                    \"field\": \"createdAt\",
                    \"direction\": \"DESC\"
                }
            ]
        }
    }"

    make_api_call "POST" "/api/queries/bills/search" "$search_data" "search-bills-response.json"
}

# Get bill statistics
get_statistics() {
    local period=${1:-"MONTHLY"}
    local year=${2:-$(date +"%Y")}
    local month=${3:-$(date +"%-m")}

    log_info "Getting bill statistics (period: $period, year: $year, month: $month)"

    make_api_call "GET" "/api/queries/bills/statistics?period=$period&year=$year&month=$month" "" "statistics-response.json"
}

# ================
# STORAGE OPERATIONS
# ================

# Generate upload URL
generate_upload_url() {
    local file_name=${1:-"example.pdf"}
    local content_type=${2:-"application/pdf"}
    local file_size=${3:-1048576}

    log_info "Generating upload URL for: $file_name"

    upload_data="{
        \"fileName\": \"$file_name\",
        \"contentType\": \"$content_type\",
        \"fileSize\": $file_size,
        \"bucket\": \"bills\",
        \"expirationMinutes\": 15
    }"

    make_api_call "POST" "/api/storage/upload-url" "$upload_data" "upload-url-response.json"
}

# Generate download URL
generate_download_url() {
    local file_id=$1

    if [ -z "$file_id" ]; then
        log_error "File ID is required"
        return 1
    fi

    log_info "Generating download URL for file: $file_id"

    make_api_call "GET" "/api/storage/files/$file_id/download-url" "" "download-url-response.json"
}

# ================
# HEALTH AND MONITORING
# ================

# Health check
health_check() {
    log_info "Performing health check..."

    response=$(curl -s "$BASE_URL/actuator/health")
    status=$(echo "$response" | jq -r '.status')

    if [ "$status" = "UP" ]; then
        log_success "Service is healthy"
        echo "$response" | jq '.' > "$OUTPUT_DIR/health-check-response.json"
    else
        log_error "Service is unhealthy"
    fi

    echo "$response"
}

# Get metrics
get_metrics() {
    log_info "Getting application metrics..."

    make_api_call "GET" "/actuator/metrics" "" "metrics-response.json"
}

# Get application info
get_info() {
    log_info "Getting application info..."

    make_api_call "GET" "/actuator/info" "" "info-response.json"
}

# ================
# WORKFLOW EXAMPLES
# ================

# Complete bill workflow example
complete_bill_workflow() {
    local file_path=$1

    log_info "Starting complete bill workflow example..."

    # Step 1: Create bill
    log_info "Step 1: Creating bill..."
    response=$(create_bill)
    bill_id=$(extract_json_value "$response" '.data.billId')

    if [ -z "$bill_id" ] || [ "$bill_id" = "null" ]; then
        log_error "Failed to create bill. Aborting workflow."
        return 1
    fi

    log_success "Bill created: $bill_id"

    # Wait a moment for the bill to be processed
    sleep 2

    # Step 2: Attach file (if provided)
    if [ -n "$file_path" ] && [ -f "$file_path" ]; then
        log_info "Step 2: Attaching file..."
        attach_response=$(attach_file "$bill_id" "$file_path")
        file_id=$(extract_json_value "$attach_response" '.data.fileId')

        if [ -n "$file_id" ] && [ "$file_id" != "null" ]; then
            log_success "File attached: $file_id"
        fi

        # Wait for OCR processing
        log_info "Waiting for OCR processing..."
        sleep 5
    else
        log_warning "No file provided. Skipping file attachment."
    fi

    # Step 3: Get bill details
    log_info "Step 3: Getting bill details..."
    get_bill_details "$bill_id"

    # Step 4: Approve bill (commented out for safety)
    # log_info "Step 4: Approving bill..."
    # approve_bill "$bill_id"

    log_success "Complete bill workflow finished!"

    echo "BILL_ID=$bill_id"
}

# Batch operations example
batch_operations() {
    local count=${1:-5}

    log_info "Creating $count sample bills..."

    bill_ids=()

    for i in $(seq 1 $count); do
        log_info "Creating bill $i of $count..."

        bill_data="{
            \"title\": \"Sample Bill $i\",
            \"total\": $((100 + i * 10)).00,
            \"vendor\": \"Vendor $i\",
            \"dueDate\": \"2024-12-$((15 + i))\",
            \"category\": \"SAMPLE\",
            \"description\": \"Sample bill number $i for testing\"
        }"

        response=$(make_api_call "POST" "/api/commands/bills" "$bill_data" "batch-create-bill-$i.json")
        bill_id=$(extract_json_value "$response" '.data.billId')

        if [ -n "$bill_id" ] && [ "$bill_id" != "null" ]; then
            bill_ids+=("$bill_id")
            log_success "Bill $i created: $bill_id"
        else
            log_warning "Failed to create bill $i"
        fi

        # Small delay between requests
        sleep 1
    done

    log_success "Batch operation completed. Created ${#bill_ids[@]} bills."

    printf '%s\n' "${bill_ids[@]}" > "$OUTPUT_DIR/batch-bill-ids.txt"
    log_info "Bill IDs saved to: $OUTPUT_DIR/batch-bill-ids.txt"
}

# Performance test
performance_test() {
    local requests=${1:-100}
    local concurrency=${2:-10}

    log_info "Running performance test: $requests requests with $concurrency concurrency"

    # Create a temporary script for concurrent requests
    temp_script=$(mktemp)

    cat > "$temp_script" << 'EOF'
#!/bin/bash

BASE_URL=$1
API_KEY=$2
REQUEST_ID=$3

response=$(curl -s -w "\nHTTP_STATUS:%{http_code}" \
    -X GET \
    -H "Content-Type: application/json" \
    -H "X-API-Key: $API_KEY" \
    -H "Accept: application/json" \
    "$BASE_URL/api/queries/bills?page=0&size=10")

http_status=$(echo "$response" | grep "HTTP_STATUS:" | cut -d: -f2)
echo "$REQUEST_ID:$http_status:$(date +%s%3N)"
EOF

    chmod +x "$temp_script"

    # Run concurrent requests
    start_time=$(date +%s%3N)

    for i in $(seq 1 "$requests"); do
        "$temp_script" "$BASE_URL" "$API_KEY" "$i" &

        # Control concurrency
        if [ $((i % concurrency)) -eq 0 ]; then
            wait
        fi
    done

    wait
    end_time=$(date +%s%3N)

    # Cleanup
    rm "$temp_script"

    duration=$((end_time - start_time))
    rps=$((requests * 1000 / duration))

    log_success "Performance test completed:"
    echo "  Requests: $requests"
    echo "  Concurrency: $concurrency"
    echo "  Duration: ${duration}ms"
    echo "  Requests per second: $rps"
}

# ================
# HELP AND USAGE
# ================

show_help() {
    cat << EOF
Billing & Expense Processing Service - API Examples

Usage: $0 [COMMAND] [OPTIONS]

COMMANDS:
  create-bill                        Create a new sample bill
  update-bill BILL_ID                Update an existing bill
  attach-file BILL_ID FILE_PATH      Attach file to bill
  approve-bill BILL_ID               Approve a bill
  reject-bill BILL_ID                Reject a bill
  delete-bill BILL_ID                Delete a bill

  list-bills [PAGE] [SIZE] [SORT]   List bills with pagination
  get-bill BILL_ID                   Get bill details
  search-bills [TITLE] [MIN] [MAX]  Search bills
  get-stats [PERIOD] [YEAR] [MONTH] Get statistics

  upload-url [FILENAME] [TYPE]      Generate upload URL
  download-url FILE_ID               Generate download URL

  health                             Health check
  metrics                            Get metrics
  info                               Get application info

  workflow [FILE_PATH]               Complete bill workflow
  batch [COUNT]                      Create multiple sample bills
  performance [REQUESTS] [CONC]      Performance test
  help                               Show this help message

ENVIRONMENT VARIABLES:
  BASE_URL     API base URL (default: http://localhost:8080)
  API_KEY      API key for authentication (default: dev-api-key)
  OUTPUT_DIR   Directory to save response samples (default: ./api/sample-responses)

EXAMPLES:
  # Create a bill and get its ID
  $0 create-bill

  # List first page of bills
  $0 list-bills 0 20 "createdAt,desc"

  # Search for office bills
  $0 search-bills "office" 50 200

  # Complete workflow with file
  $0 workflow /path/to/invoice.pdf

  # Performance test
  $0 performance 100 10

EOF
}

# ================
# MAIN SCRIPT
# ================

main() {
    case "${1:-help}" in
        "create-bill")
            create_bill
            ;;
        "update-bill")
            update_bill "$2"
            ;;
        "attach-file")
            attach_file "$2" "$3"
            ;;
        "approve-bill")
            approve_bill "$2"
            ;;
        "reject-bill")
            reject_bill "$2"
            ;;
        "delete-bill")
            delete_bill "$2"
            ;;
        "list-bills")
            list_bills "$2" "$3" "$4" "$5" "$6"
            ;;
        "get-bill")
            get_bill_details "$2"
            ;;
        "search-bills")
            search_bills "$2" "$3" "$4"
            ;;
        "get-stats")
            get_statistics "$2" "$3" "$4"
            ;;
        "upload-url")
            generate_upload_url "$2" "$3" "$4"
            ;;
        "download-url")
            generate_download_url "$2"
            ;;
        "health")
            health_check
            ;;
        "metrics")
            get_metrics
            ;;
        "info")
            get_info
            ;;
        "workflow")
            complete_bill_workflow "$2"
            ;;
        "batch")
            batch_operations "$2"
            ;;
        "performance")
            performance_test "$2" "$3"
            ;;
        "help"|"-h"|"--help")
            show_help
            ;;
        *)
            log_error "Unknown command: $1"
            show_help
            exit 1
            ;;
    esac
}

# Check dependencies
if ! command -v curl &> /dev/null; then
    log_error "curl is required but not installed"
    exit 1
fi

if ! command -v jq &> /dev/null; then
    log_warning "jq is not installed. JSON parsing may be limited"
fi

# Run main function
main "$@"